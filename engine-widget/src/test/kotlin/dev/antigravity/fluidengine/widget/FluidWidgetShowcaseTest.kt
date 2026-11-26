package dev.antigravity.fluidengine.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.LocalSize
import androidx.lifecycle.Lifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F8.25 · La vetrina dei widget (`vetrina-dei-widget`): l'anteprima vera di Glance disposta alla misura della taglia
 * (e solo rimpicciolita se la colonna è stretta), la richiesta al launcher coi suoi esiti trattati con prudenza
 * (aggiunto col ritorno, aggiunto senza ritorno, chiuso, launcher muto, non supportato), TalkBack (l'anteprima è
 * un'immagine sola, le taglie si cambiano con le azioni) e «Riduci movimento».
 */
@RunWith(RobolectricTestRunner::class)
// Lo schermo di un telefono, il bersaglio (Galaxy S25: 411×891 dp a 420 dpi). Su quello di serie di Robolectric
// (320×470 dp) la scheda e' piu' alta dello schermo e il tasto resta fuori.
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FluidWidgetShowcaseTest {

  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val context get() = RuntimeEnvironment.getApplication()
  private val provider by lazy { ComponentName(context, "dev.prova.AgendaWidget") }
  private val manager get() = AppWidgetManager.getInstance(context)

  private val agenda by lazy {
    FluidWidgetEntry(
      provider = provider,
      title = "Agenda",
      description = "Gli impegni di oggi, uno per riga.",
      preview = FluidWidgetPreview.Glance { AgendaContent() },
    )
  }

  @Composable
  private fun AgendaContent() {
    val size = LocalSize.current
    val palette = engineWidgetPalette(context, EngineSettings())
    val layout = resolveEngineWidgetLayout(size)
    EngineWidgetSurface(palette, layout) {
      EngineWidgetHeader(title = "Agenda", subtitle = "Oggi", palette = palette, layout = layout)
    }
  }

  @Before
  fun installProvider() {
    shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { provider = this@FluidWidgetShowcaseTest.provider })
    shadowOf(manager).setRequestPinAppWidgetSupported(true)
  }

  /**
   * Un launcher che accetta la richiesta e non risponde: il widget c'e' solo se la prova lo mette ([ids]). Quello di
   * Robolectric invece accetta e risponde subito, come il launcher dei Pixel.
   */
  private class QuietLauncher(override val isRequestPinSupported: Boolean = true) : FluidWidgetLauncher {
    val ids = LinkedHashSet<Int>()
    var requests = 0
    override fun requestPin(provider: ComponentName, callback: android.app.PendingIntent): Boolean {
      requests++
      return true
    }
    override fun ids(provider: ComponentName): Set<Int> = ids.toSet()
  }

  private fun showcase(reduced: Boolean = false, launcher: FluidWidgetLauncher? = null) {
    rule.setContent {
      FluidTheme(EngineSettings()) {
        FluidMotionPolicyProvider(if (reduced) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy.Default) {
          androidx.compose.runtime.CompositionLocalProvider(LocalFluidWidgetLauncher provides launcher) {
            FluidWidgetShowcase(listOf(agenda), appName = "Pampa Widgets")
          }
        }
      }
    }
    rule.waitForIdle()
  }

  @Test
  fun `la scala dell'anteprima - mai ingrandita, rimpicciolita solo se serve`() {
    assertEquals(1f, fluidWidgetPreviewScale(DpSize(158.dp, 158.dp), 400.dp))
    assertEquals(200f / 338f, fluidWidgetPreviewScale(DpSize(338.dp, 158.dp), 200.dp), 1e-4f)
    assertEquals(1f, fluidWidgetPreviewScale(DpSize(338.dp, 158.dp), 0.dp))
  }

  @Test
  fun `l'anteprima vera e' disposta alla misura della taglia`() {
    rule.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.width(400.dp)) {
          FluidWidgetPreviewBox(agenda, FluidWidgetSize.Medium, contentDescription = "Agenda media")
        }
      }
    }
    rule.waitUntil(5_000) { frames().isNotEmpty() }
    val frame = frames().single()
    val density = context.resources.displayMetrics.density
    assertEquals((338 * density).toInt().toFloat(), frame.width.toFloat(), 1.5f)
    assertEquals((158 * density).toInt().toFloat(), frame.height.toFloat(), 1.5f)
    // Sono le RemoteViews di Glance, applicate come farebbe il launcher: il titolo del widget c'e'.
    assertTrue(texts(frame).contains("Agenda"))
    // Per TalkBack e' un'immagine sola: i figli sono nascosti.
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, frame.importantForAccessibility)
    rule.onNodeWithTag(FluidWidgetShowcaseTags.preview("Medio")).assertWidthIsEqualTo(338.dp).assertHeightIsEqualTo(158.dp)
  }

  @Test
  fun `in una colonna stretta l'anteprima si rimpicciolisce ma resta disposta alla sua misura`() {
    rule.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.width(200.dp)) {
          FluidWidgetPreviewBox(agenda, FluidWidgetSize.Medium, contentDescription = "Agenda media")
        }
      }
    }
    rule.waitUntil(5_000) { frames().isNotEmpty() }
    val density = context.resources.displayMetrics.density
    assertEquals((338 * density).toInt().toFloat(), frames().single().width.toFloat(), 1.5f)
    rule.onNodeWithTag(FluidWidgetShowcaseTags.preview("Medio"))
      .assertWidthIsEqualTo(200.dp)
      .assertHeightIsEqualTo(158.dp * (200f / 338f))
  }

  @Test
  fun `il launcher di Robolectric accetta e risponde subito - Aggiunto alla Home`() {
    showcase()
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    shadowOf(android.os.Looper.getMainLooper()).idle()
    rule.waitForIdle()
    assertMessage("Aggiunto alla Home.")
    assertTrue("il widget e' davvero fra quelli del provider", manager.getAppWidgetIds(provider).isNotEmpty())
  }

  @Test
  fun `il launcher risponde dopo - Aggiunto alla Home`() {
    val launcher = QuietLauncher()
    showcase(launcher = launcher)
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    assertMessage("Conferma sulla Home.")
    assertEquals(1, launcher.requests)
    // Il launcher mette il widget e manda il ritorno col suo id.
    launcher.ids += 7
    context.sendBroadcast(
      Intent(FluidWidgetPin.callbackAction(context)).setPackage(context.packageName)
        .putExtra(FluidWidgetPin.ExtraProvider, provider.flattenToString())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7),
    )
    shadowOf(android.os.Looper.getMainLooper()).idle()
    rule.waitForIdle()
    assertMessage("Aggiunto alla Home.")
  }

  @Test
  fun `il ritorno non arriva ma il widget c'e' - Aggiunto lo stesso`() {
    val launcher = QuietLauncher()
    showcase(launcher = launcher)
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    launcher.ids += 9
    rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    rule.waitForIdle()
    assertMessage("Aggiunto alla Home.")
  }

  @Test
  fun `l'utente chiude la richiesta - Non aggiunto, dopo il secondo sguardo`() {
    showcase(launcher = QuietLauncher())
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    rule.waitForIdle()
    // Subito dopo il ritorno non si dice ancora niente: il launcher a volte mette il widget un attimo dopo.
    assertMessage("Conferma sulla Home.")
    rule.mainClock.advanceTimeBy(FluidWidgetPin.RecheckMillis + 100)
    rule.waitForIdle()
    assertMessage("Non aggiunto. Puoi riprovare quando vuoi.")
  }

  @Test
  fun `il launcher dice di si' e non mostra niente - la strada a mano`() {
    showcase(launcher = QuietLauncher())
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    rule.mainClock.advanceTimeBy(FluidWidgetPin.NoAnswerMillis + 100)
    rule.waitForIdle()
    assertMessage(
      "Il launcher non ha risposto. Tieni premuto su uno spazio vuoto della Home, tocca «Widget» e cerca «Pampa Widgets».",
    )
  }

  @Test
  fun `il launcher non permette la richiesta - si spiega e non si chiede niente`() {
    shadowOf(manager).setRequestPinAppWidgetSupported(false)
    showcase()
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    assertMessage(
      "Questo launcher non permette di aggiungere widget da un'app. Tieni premuto su uno spazio vuoto della Home, " +
        "tocca «Widget» e cerca «Pampa Widgets».",
    )
  }

  @Test
  fun `TalkBack - l'anteprima ha il suo nome e le taglie si cambiano con le azioni`() {
    showcase()
    rule.onNode(
      SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Anteprima del widget Agenda, taglia Piccolo")),
    ).assertExists()
    rule.onNodeWithText("Piccolo · 2×2").assertExists()
    val card = rule.onNodeWithTag(FluidWidgetShowcaseTags.card("Agenda"))
    val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
    assertEquals(listOf("Taglia dopo", "Taglia prima"), actions.map { it.label })
    rule.runOnIdle { actions.first().action() }
    rule.mainClock.advanceTimeBy(2_000)
    rule.waitForIdle()
    rule.onNodeWithText("Medio · 4×2").assertExists()
  }

  @Test
  fun `Riduci movimento - la taglia cambia senza scorrere e l'esito compare fermo`() {
    showcase(reduced = true, launcher = QuietLauncher())
    val card = rule.onNodeWithTag(FluidWidgetShowcaseTags.card("Agenda"))
    val next = card.fetchSemanticsNode().config[SemanticsActions.CustomActions].first()
    rule.mainClock.autoAdvance = false
    rule.runOnIdle { next.action() }
    // Un fotogramma basta: niente animazione di scorrimento.
    rule.mainClock.advanceTimeByFrame()
    rule.mainClock.advanceTimeByFrame()
    rule.onNodeWithText("Medio · 4×2").assertExists()
    rule.onNodeWithTag(FluidWidgetShowcaseTags.Add).performClick()
    rule.mainClock.advanceTimeByFrame()
    assertMessage("Conferma sulla Home.")
    rule.mainClock.autoAdvance = true
  }

  @Test
  fun `gli esiti del riquadro rapido e le sue parole`() {
    assertEquals(FluidQuickTileState.Added, FluidQuickTileRequest.quickTileStateOf(2))
    assertEquals(FluidQuickTileState.AlreadyAdded, FluidQuickTileRequest.quickTileStateOf(1))
    assertEquals(FluidQuickTileState.NotAdded, FluidQuickTileRequest.quickTileStateOf(0))
    assertEquals(FluidQuickTileState.Unsupported, FluidQuickTileRequest.quickTileStateOf(-3))
    val words = FluidQuickTileStrings()
    assertEquals(null, words.messageFor(FluidQuickTileState.Asking))
    assertEquals("Nelle Impostazioni rapide.", words.messageFor(FluidQuickTileState.Added))
    assertEquals("È già nelle Impostazioni rapide.", words.messageFor(FluidQuickTileState.AlreadyAdded))
  }

  @Test
  fun `la vetrina mostra solo i widget, mai il riquadro rapido`() {
    showcase(launcher = QuietLauncher())
    rule.onNodeWithTag(FluidWidgetShowcaseTags.card("Agenda")).assertExists()
    rule.onNodeWithTag(FluidQuickTileTags.Card).assertDoesNotExist()
    rule.onNodeWithText("Aggiungi alle Impostazioni rapide").assertDoesNotExist()
  }

  private val tileOffer by lazy {
    FluidQuickTileOffer(
      service = ComponentName(context, "dev.prova.RiquadroRapido"),
      label = "Timer",
      description = "Il timer di Sounds dalle Impostazioni rapide.",
      icon = android.R.drawable.ic_menu_add,
    )
  }

  private fun quickTileCard() {
    rule.setContent { FluidTheme(EngineSettings()) { FluidQuickTileCard(tileOffer) } }
    rule.waitForIdle()
  }

  @Test
  fun `la scheda del riquadro per le impostazioni dell'app ha nome, frase e tasto`() {
    quickTileCard()
    rule.onNodeWithTag(FluidQuickTileTags.Card).assertExists()
    rule.onNodeWithText("Timer").assertExists()
    rule.onNodeWithText("Il timer di Sounds dalle Impostazioni rapide.").assertExists()
    rule.onNodeWithTag(FluidQuickTileTags.Add).assertExists()
    // Da Android 13 non c'e' niente da dire prima del tocco.
    rule.onNodeWithTag(FluidQuickTileTags.Message).assertDoesNotExist()
  }

  @Test
  @Config(sdk = [32], qualifiers = "w411dp-h891dp-xxhdpi")
  fun `prima di Android 13 la scheda del riquadro dice la strada a mano`() {
    quickTileCard()
    rule.onNodeWithText(
      "Si può proporre da Android 13: aprilo dalle Impostazioni rapide, tocca la matita e trascinalo su.",
    ).assertExists()
  }

  /** Il testo dell'esito sotto il tasto (con il testo vero nel messaggio, se non e' quello atteso). */
  private fun assertMessage(expected: String) {
    val nodes = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(FluidWidgetShowcaseTags.Message)).fetchSemanticsNodes()
    val actual = nodes.map { n -> n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString { it.text } }
    assertEquals(listOf(expected), actual)
  }

  private fun frames(): List<PreviewFrame> {
    val out = ArrayList<PreviewFrame>()
    fun walk(v: View) {
      if (v is PreviewFrame) out += v
      if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    walk(rule.activity.window.decorView)
    return out.filter { it.childCount > 0 }
  }

  private fun texts(v: View): List<String> {
    val out = ArrayList<String>()
    fun walk(x: View) {
      if (x is TextView) out += x.text.toString()
      if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
    }
    walk(v)
    return out
  }
}
