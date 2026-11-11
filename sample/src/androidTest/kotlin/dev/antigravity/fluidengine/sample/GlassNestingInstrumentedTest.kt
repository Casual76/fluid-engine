package dev.antigravity.fluidengine.sample

import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialNestedSheet
import dev.antigravity.fluidengine.sample.material.MaterialSiblingsPanel
import dev.antigravity.fluidengine.ui.fluid.FluidAppearance
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationAction
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Il vetro dentro il vetro, **composto davvero** e disegnato dal dispositivo (giro di correzione di F1A; rilievo dei
 * critici: «nessun test compone un albero vero di vetro dentro vetro», ed è lì che l'anello vuoto della notifica è
 * arrivato al tablet invece di fermarsi al PC).
 *
 * Quattro alberi, con i componenti veri: la notifica **senza fondo** (la card piena che pubblica il suo colore), la
 * notifica **di vetro** (la card `Liquid` che esporta, i controlli sul fondo combinato), la scena `nested` della pagina
 * «Materiale» (il tasto figlio di un foglio di vetro, sul fondo della pagina) e la scena `siblings` (i tasti in un nodo
 * fratello del pannello, sul fondo combinato). Per ognuno, nei due temi e con la regola dell'annidato accesa e spenta
 * (`gallery.tune nested=on|off`, la stessa manopola delle sessioni), si leggono dai pixel il corpo di un tasto tondo
 * (una corona fra l'icona e il filo) e il pannello subito attorno (un anello appena fuori dal filo, la mediana: il
 * testo che passa dietro un pannello di vetro non la sposta). Il pannello si legge lì, e non su una striscia a parte,
 * perché il confronto vale solo sullo stesso fondo: sopra l'azione della notifica di vetro passa il testo della pagina,
 * e quella striscia dava 45,1 contro i 48,8 del tasto (il primo giro sul Tab S9), un confronto fra due fondi diversi.
 *
 * Sul tema scuro si chiede: il corpo del tasto **non è** quello del pannello (l'anello vuoto era 56 su 56), e accesa e
 * spenta la regola **danno due corpi diversi** (la regola è arrivata). Sul chiaro la regola tiene il velo (cambia solo
 * l'ombra): i numeri si scrivono in logcat (`GlassNesting`) e non si giudicano qui.
 *
 * In più, la forma: una `RoundedCornerShape` con un angolo oltre mezzo lato corto resta `Classic` sul dispositivo, e
 * il fumetto di chat alto abbastanza resta `Liquid` (punto 1 del giro di correzione).
 */
@RunWith(AndroidJUnit4::class)
class GlassNestingInstrumentedTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    assumeTrue("Il materiale nuovo esiste da Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
  }

  @After
  fun stop() {
    compose.runOnUiThread { FluidGlassDiagnostics.tuneLiquidProfile(compose.activity, null) }
    FluidGlassDiagnostics.enabled = diagnosticsWere
  }

  private enum class Case { OpaqueNotification, GlassNotification, Nested, Siblings }

  private var dark by mutableStateOf(true)
  private var generation by mutableIntStateOf(0)

  @Composable
  private fun Scene(case: Case) {
    val settings = remember(dark) {
      FluidAppearance.withThemeMode(EngineSettings(), if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }
    FluidTheme(settings = settings) {
      val stage = rememberGlassBackdrop()
      Box(Modifier.fillMaxSize()) {
        // Il palco: righe di testo sulla pagina del tema, come il fondo `text` della pagina «Materiale».
        Column(
          modifier = Modifier
            .fillMaxSize()
            .glassBackdropSource(stage)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          repeat(40) {
            Text(
              "Signs are made using neon lights. Light is used to define color, tone, and shape.",
              color = MaterialTheme.colorScheme.onBackground,
            )
          }
        }
        // Una generazione nuova a ogni manopola: le lastre tengono il profilo con cui sono nate.
        key(generation) {
          when (case) {
            Case.Nested -> Box(Modifier.padding(top = 120.dp, start = 24.dp)) {
              MaterialNestedSheet(stage = stage, round = Modifier.testTag(Round))
            }
            Case.Siblings -> Box(Modifier.padding(top = 120.dp, start = 24.dp)) {
              MaterialSiblingsPanel(stage = stage, round = Modifier.testTag(Round))
            }
            Case.OpaqueNotification -> NotificationScene(backdrop = null)
            Case.GlassNotification -> NotificationScene(backdrop = stage)
          }
        }
      }
    }
  }

  @Composable
  private fun NotificationScene(backdrop: GlassBackdropState?) {
    val host = rememberFluidNotificationHostState()
    LaunchedEffect(host) {
      host.show(
        FluidNotification(
          id = "annidato-$generation",
          title = "Operazione aggiornata",
          message = "Ultima modifica disponibile",
          // Lunga: il test fa avanzare l'orologio di pochi secondi, la notifica deve restare.
          durationMillis = 600_000L,
          inlineActions = listOf(FluidNotificationAction(ActionLabel) {}),
        ),
      )
    }
    Box(Modifier.fillMaxSize().padding(top = 96.dp)) {
      FluidNotificationHost(host, backdrop = backdrop)
    }
  }

  private class Reading(val body: Double, val panel: Double)

  private fun luma(pixels: PixelMap, x: Int, y: Int): Double {
    val c = pixels[x, y]
    return 255.0 * (0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue)
  }

  private fun bounds(matcher: SemanticsMatcher): Rect =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

  /** La media della corona fra 0,62 e 0,86 del raggio del vetro (44 dp) attorno al centro di [r]: senza icona né filo. */
  private fun ring(pixels: PixelMap, r: Rect, density: Float): Double {
    val radius = 22f * density
    var sum = 0.0
    var n = 0
    val cx = r.center.x
    val cy = r.center.y
    for (y in (cy - radius).toInt()..(cy + radius).toInt()) {
      for (x in (cx - radius).toInt()..(cx + radius).toInt()) {
        val d = hypot(x + 0.5f - cx, y + 0.5f - cy)
        if (d < 0.62f * radius || d > 0.86f * radius) continue
        if (x < 0 || y < 0 || x >= pixels.width || y >= pixels.height) continue
        sum += luma(pixels, x, y)
        n++
      }
    }
    return sum / n
  }

  /**
   * La mediana dell'anello fra 1,15 e 1,32 del raggio del vetro (44 dp) attorno al centro di [r]: il pannello subito
   * fuori dal filo del tasto, sullo stesso fondo. Solo i pixel dentro [clip], quando c'è (la card della notifica: la
   * croce sta in un angolo, e l'anello uscirebbe sulla pagina).
   */
  private fun around(pixels: PixelMap, r: Rect, density: Float, clip: Rect? = null): Double {
    val radius = 22f * density
    val outer = 1.32f * radius
    val values = ArrayList<Double>()
    val cx = r.center.x
    val cy = r.center.y
    for (y in (cy - outer).toInt()..(cy + outer).toInt()) {
      for (x in (cx - outer).toInt()..(cx + outer).toInt()) {
        val d = hypot(x + 0.5f - cx, y + 0.5f - cy)
        if (d < 1.15f * radius || d > outer) continue
        if (x < 0 || y < 0 || x >= pixels.width || y >= pixels.height) continue
        if (clip != null && !clip.contains(Offset(x + 0.5f, y + 0.5f))) continue
        values += luma(pixels, x, y)
      }
    }
    values.sort()
    return values[values.size / 2]
  }

  private fun read(case: Case): Reading {
    val pixels = compose.onRoot().captureToImage().toPixelMap()
    val density = compose.activity.resources.displayMetrics.density
    return when (case) {
      Case.Nested, Case.Siblings -> {
        val round = bounds(hasTestTag(Round))
        Reading(body = ring(pixels, round, density), panel = around(pixels, round, density))
      }
      Case.OpaqueNotification, Case.GlassNotification -> {
        // La croce: il tasto tondo di vetro della card. Il pannello: l'anello attorno alla croce, dentro la card (il
        // rettangolo che va dal titolo alla croce e giù fino all'azione, tutto dentro la card).
        val close = bounds(
          (hasContentDescription("Chiudi notifica") or hasContentDescription("Close notification")),
        ).let { icon ->
          // La descrizione sta sull'icona: il tasto è il nodo cliccabile che la contiene.
          compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.contains(icon.center) }
            .minByOrNull { it.width * it.height } ?: icon
        }
        val action = bounds(hasText(ActionLabel))
        val actionButton = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
          .map { it.boundsInRoot }
          .filter { it.contains(action.center) && it.width > action.width }
          .minByOrNull { it.width * it.height } ?: action
        val title = bounds(hasText("Operazione aggiornata"))
        val card = Rect(
          left = minOf(title.left, actionButton.left),
          top = minOf(title.top, close.top),
          right = close.right,
          bottom = actionButton.bottom,
        )
        Reading(body = ring(pixels, close, density), panel = around(pixels, close, density, clip = card))
      }
    }
  }

  private fun settle() {
    // L'orologio della composizione avanza a mano: la notifica ha un conto alla rovescia, e l'attesa automatica lo
    // farebbe scadere. Due secondi e mezzo coprono l'entrata e un giro degli effetti.
    repeat(150) { compose.mainClock.advanceTimeByFrame() }
    compose.waitForIdle()
  }

  private fun runCase(case: Case) {
    compose.mainClock.autoAdvance = false
    compose.setContent { Scene(case) }
    val results = LinkedHashMap<String, Reading>()
    for (theme in listOf(true, false)) {
      for (nested in listOf("on", "off")) {
        compose.runOnUiThread {
          FluidGlassDiagnostics.tuneLiquidProfile(compose.activity, mapOf("nested" to nested))
          dark = theme
          generation++
        }
        settle()
        val reading = read(case)
        val name = "${if (theme) "scuro" else "chiaro"} nested=$nested"
        results[name] = reading
        Log.i(Tag, "$case $name: tasto ${"%.1f".format(reading.body)} pannello ${"%.1f".format(reading.panel)}")
      }
    }
    val on = results.getValue("scuro nested=on")
    val off = results.getValue("scuro nested=off")
    assertTrue(
      "$case, scuro: il corpo del tasto (${on.body}) e' quello del pannello (${on.panel}): un anello vuoto",
      abs(on.body - on.panel) >= MinBodyStep,
    )
    assertTrue(
      "$case, scuro: con la regola accesa (${on.body}) e spenta (${off.body}) il tasto e' lo stesso: la regola non e' arrivata",
      abs(on.body - off.body) >= MinRuleStep,
    )
  }

  @Test
  fun anOpaqueNotificationHoldsANestedButtonWithABody() = runCase(Case.OpaqueNotification)

  @Test
  fun aGlassNotificationHoldsANestedButtonWithABody() = runCase(Case.GlassNotification)

  @Test
  fun theNestedSceneGetsTheRuleFromTheNode() = runCase(Case.Nested)

  @Test
  fun theSiblingsSceneGetsTheRuleFromTheBackdrop() = runCase(Case.Siblings)

  // ---- La forma (punto 1) -------------------------------------------------------------------------------------

  private var bubbleHeight by mutableStateOf(100.dp)
  private var bubbleShape by mutableStateOf(RoundedCornerShape(topStart = 80.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))

  @Test
  fun aShapeWithACornerPastHalfTheShortSideStaysClassicOnTheDevice() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Liquid) {
          val stage = rememberGlassBackdrop()
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(stage).background(MaterialTheme.colorScheme.background))
            Box(
              Modifier
                .padding(40.dp)
                .size(200.dp, bubbleHeight)
                .glassSurface(state = stage, tint = GlassDefaults.panelTint(), shape = bubbleShape, role = GlassRole.Content),
            )
          }
        }
      }
    }
    fun choices(): Map<FluidGlassModelReason, Int> {
      compose.waitForIdle()
      return FluidGlassDiagnostics.liveModelChoices.filterValues { it != 0 }
    }
    // L'angolo da 80 su un lato corto di 100: Compose lo disegna, il campo no. Dopo la prima misura, `Classic`.
    assertEquals(mapOf(FluidGlassModelReason.GenericShape to 1), choices())
    // La stessa forma alta 160: l'angolo sta in mezzo lato, il campo la rende.
    compose.runOnIdle { bubbleHeight = 160.dp }
    assertEquals(mapOf(FluidGlassModelReason.Liquid to 1), choices())
    // Il fumetto di chat: a 40 dp l'angolo da 22 passa mezzo lato (Compose porta l'altro fianco a 20), a 44 no.
    compose.runOnIdle {
      bubbleShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
      bubbleHeight = 40.dp
    }
    assertEquals(mapOf(FluidGlassModelReason.GenericShape to 1), choices())
    compose.runOnIdle { bubbleHeight = 44.dp }
    assertEquals(mapOf(FluidGlassModelReason.Liquid to 1), choices())
    Log.i(Tag, "forme: scelte ${FluidGlassDiagnostics.liveModelChoices}")
  }

  private companion object {
    const val Tag = "GlassNesting"
    const val Round = "annidato-tondo"
    const val ActionLabel = "Ripristina ultima modifica"

    /** Il corpo di un tasto annidato sta almeno tanti livelli dal pannello (l'anello vuoto: 0). */
    const val MinBodyStep = 4.0

    /** Accesa e spenta, la regola dell'annidato sposta il corpo di almeno tanti livelli sul tema scuro. */
    const val MinRuleStep = 6.0
  }
}
