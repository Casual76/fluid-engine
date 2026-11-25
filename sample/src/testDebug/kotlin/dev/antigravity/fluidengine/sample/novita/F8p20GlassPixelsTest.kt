package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsuleTags
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.max
import kotlin.math.min
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.20b · Il vetro della capsula della rete, del suo pannello e della scheda della pillola, coi pixel della finestra
 * (PixelCopy), **coi verdetti degli shader rifiutati** (il vetro di ripiego: il corpo grande del materiale `Liquid` non
 * compila nel Robolectric, vedi [F8p8bPopoverPixelsTest]).
 *
 * Sul Tab S9 (25/11, debug sopra `48daf53`) sotto il pannello e sotto la scheda il testo della pagina si leggeva nitido.
 * La prova mette una pagina di testo nero su bianco sotto ognuno dei tre vetri. Per il pannello e la card misura
 * l'escursione della luminanza (99° meno 1° percentile) in due strisce **dentro il bordo** del vetro, dove sopra non c'e'
 * contenuto: il testo nitido ne ha piu' di 150, il testo smerigliato meno di [SPREAD_LIMIT] (la soglia di F8.8b). Ogni
 * pannello due volte: col tetto del vetro libero e **col tetto esaurito**, come dopo un primo rifiuto sul tablet: una
 * cattura negata non deve mai lasciare vetro limpido (F7.18). Le immagini finiscono in `sample/build/f8p20-pixels/`.
 *
 * **Cosa ha trovato** (prima della correzione): col tetto pieno il pannello della capsula lasciava 106 su 255 (il testo
 * nitido sotto un velo, come sul tablet). Il nodo del pannello trovava la lettura della memoria vecchia e aspettava senza
 * cattura e senza pieno. Ora un pannello davanti non aspetta la lettura (`DrawBackdropNode`): 2. F7.19: senza la regola la
 * fotografia dopo (100 ms) dava gia' 2, anche prima di F7.19; quindi qui era il solo primo fotogramma, e la regola lo toglie.
 *
 * La capsula sta nell'`overlay` della schermata e rifrange la pagina, come la consiglia la scheda; e la capsula di serie
 * (posata in un `Box` sopra il contenuto, senza un fondale) e' piena. Per la capsula, piccola e con le estremita' tonde, la
 * misura e' un'altra: la pagina cambia lettere sotto di lei e si guarda quanto del cambio passa attraverso il vetro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p20GlassPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: android.view.View
  private var hold: FluidMemoryBudget.Reservation? = null
  private val px = 2f

  @Before fun start() {
    // Il registro del vetro (glassBudgetDenied, glassDeniedFill): solo per capire chi viene negato.
    if (GlassLogForDebug) {
      org.robolectric.shadows.ShadowLog.setLoggable("FluidGlassFrame", android.util.Log.VERBOSE)
      org.robolectric.shadows.ShadowLog.stream = System.out
    }
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    hold?.close()
    hold = null
    FluidGlassDiagnostics.resetForTesting()
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

  private fun tap(at: Offset) {
    val finger = MaterialFinger(view)
    finger.down(at)
    advance(80)
    finger.up(at)
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8p20-pixels").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun luminance(image: Bitmap, x: Int, y: Int): Float {
    val pixel = image.getPixel(x, y)
    return 0.2126f * android.graphics.Color.red(pixel) + 0.7152f * android.graphics.Color.green(pixel) +
      0.0722f * android.graphics.Color.blue(pixel)
  }

  /** L'escursione della luminanza nel ritaglio: 99° meno 1° percentile, su 255. */
  private fun spread(image: Bitmap, area: Rect): Float {
    val values = ArrayList<Float>()
    val x0 = max(0, area.left.toInt()); val x1 = min(image.width - 1, area.right.toInt())
    val y0 = max(0, area.top.toInt()); val y1 = min(image.height - 1, area.bottom.toInt())
    for (y in y0..y1) for (x in x0..x1) values += luminance(image, x, y)
    values.sort()
    return values[(values.size * 0.99f).toInt().coerceAtMost(values.size - 1)] - values[(values.size * 0.01f).toInt()]
  }

  /** Le due strisce dentro il bordo di un vetro: [inset] dal bordo, larghe [width] (dp), senza gli angoli. */
  private fun edgeStrips(glass: Rect, inset: Float = 3f, width: Float = 8f, corner: Float = 22f): List<Rect> = listOf(
    Rect(glass.left + inset * px, glass.top + corner * px, glass.left + (inset + width) * px, glass.bottom - corner * px),
    Rect(glass.right - (inset + width) * px, glass.top + corner * px, glass.right - inset * px, glass.bottom - corner * px),
  )

  /** Il vetro di un modale aperto: il nodo col titolo del pannello. */
  private fun pane(): Rect = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
    .fetchSemanticsNodes().last().boundsInRoot

  /** Il tetto del vetro pieno, come dopo un primo rifiuto: ogni cattura grande che arriva dopo e' negata. */
  private fun exhaustGlassBudget() {
    val budget = FluidGlassResources.get(compose.activity).budget
    val glass = budget.usage(FluidMemoryCategory.GLASS)
    val process = budget.usage()
    val free = min(glass.limitBytes - glass.usedBytes, process.limitBytes - process.usedBytes)
    if (free > 0) hold = budget.tryReserve(FluidMemoryCategory.GLASS, free)
  }

  private var online by mutableStateOf(true)
  private var variant by mutableStateOf(0)
  private val status = SyncStatus(pending = 3)

  /** Una pagina di testo nero su bianco, piena da cima a fondo, nella chrome della galleria (il fondale eletto). */
  private fun page(
    activities: FluidActivityHostState = FluidActivityHostState(),
    capsuleInOverlay: Boolean = true,
  ) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val chrome = rememberFluidChromeController()
        val host = rememberFluidGlassModalHostState()
        val fallback = rememberGlassBackdrop()
        val backdrop = chrome.activeBackdrop.value ?: fallback
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            ProvideFluidChrome(controller = chrome, bottomInset = 0.dp, scrollToTop = remember { FluidScrollToTopBus() }) {
              Box(Modifier.fillMaxSize().fluidChromePages()) {
                FluidActivityHost(activities) {
                  Box(Modifier.fillMaxSize()) {
                    FluidScreen(
                      title = "Le tue app",
                      onBack = {},
                      // Il titolo in linea, come le scene: la pillola sta al posto del titolo nella barra.
                      toolbar = { title(FluidToolbarTitleMode.Inline) },
                      overlay = { pageGlass ->
                        if (capsuleInOverlay) {
                          FluidConnectionCapsule(
                            online = online,
                            status = status,
                            onSyncNow = {},
                            backdrop = pageGlass,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                          )
                        }
                      },
                    ) {
                      items(60) {
                        BasicText(
                          if (variant == 0) {
                            "Spesa, gita a Firenze, compiti 2026-11-25, idee per il regalo, libri da leggere"
                          } else {
                            "QWMH BXKZ 8848 WWMM HHXX QQMM 0000 KKWW MMHH BBXX ZZQQ 1111 WWHH MMKK XXZZ BBMM"
                          },
                          style = TextStyle(fontSize = 17.sp, color = Color.Black),
                          maxLines = 1,
                        )
                      }
                    }
                    if (!capsuleInOverlay) {
                      // Come la scheda la mostrava: un Box sopra il contenuto, senza fondale.
                      FluidConnectionCapsule(
                        online = online,
                        status = status,
                        onSyncNow = {},
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                      )
                    }
                  }
                }
              }
            }
            FluidGlassModalHost(state = host, backdrop = backdrop)
          }
        }
      }
    }
    advance(900)
  }

  private fun capsule(): Rect = compose.onNodeWithTag(FluidConnectionCapsuleTags.Capsule, useUnmergedTree = true)
    .fetchSemanticsNode().boundsInRoot

  private fun check(name: String, bare: Bitmap, open: Bitmap, strips: List<Rect>) {
    val before = strips.map { spread(bare, it) }
    val after = strips.map { spread(open, it) }
    println("F8p20b $name: pagina nuda ${before.map { "%.0f".format(it) }}, sotto il vetro ${after.map { "%.0f".format(it) }}, strisce $strips")
    for (i in strips.indices) {
      if (before[i] < 150f) continue
      assertTrue("$name: sotto il vetro il testo si legge (striscia $i, escursione ${after[i]})", after[i] < SPREAD_LIMIT)
    }
    assertTrue("$name: nessuna striscia ha testo sotto: $before", before.any { it >= 150f })
  }

  private fun panelOfCapsule(exhausted: Boolean) {
    page()
    online = false
    advance(900)
    val glass = capsule()
    val bare = capture("capsula-chiusa${if (exhausted) "-tetto" else ""}")
    if (exhausted) exhaustGlassBudget()
    tap(glass.center)
    advance(1600)
    val open = capture("capsula-pannello${if (exhausted) "-tetto" else ""}")
    check("pannello${if (exhausted) " (tetto pieno)" else ""}", bare, open, edgeStrips(pane()))
  }

  @Test fun `sotto il pannello della capsula il testo della pagina non si legge`() = panelOfCapsule(exhausted = false)

  @Test fun `col tetto del vetro pieno il pannello della capsula non lascia vetro limpido`() = panelOfCapsule(exhausted = true)

  private fun cardOfPill(exhausted: Boolean) {
    val activities = FluidActivityHostState()
    page(activities, capsuleInOverlay = false)
    online = true
    activities.publish(FluidActivity.transfer(id = "notes", progress = { 0.4f }, title = "Pampa Notes 2.4", onPause = {}, onCancel = {}, onOpen = {}))
    advance(1200)
    val pills = compose.onAllNodesWithContentDescription("Pampa Notes", substring = true).fetchSemanticsNodes()
    assertTrue("la pillola non c'e'", pills.isNotEmpty())
    val pill = pills.first().boundsInRoot
    val bare = capture("pillola-chiusa${if (exhausted) "-tetto" else ""}")
    if (exhausted) exhaustGlassBudget()
    tap(pill.center)
    advance(1600)
    val open = capture("pillola-scheda${if (exhausted) "-tetto" else ""}")
    check("scheda della pillola${if (exhausted) " (tetto pieno)" else ""}", bare, open, edgeStrips(pane(), corner = 16f))
  }

  @Test fun `sotto la scheda della pillola il testo della pagina non si legge`() = cardOfPill(exhausted = false)

  @Test fun `col tetto del vetro pieno la scheda della pillola non lascia vetro limpido`() = cardOfPill(exhausted = true)

  /**
   * Quanto passa attraverso la capsula: la pagina cambia testo sotto di lei (stesse righe, altre lettere) e si misura il
   * 99° percentile della differenza di luminanza dentro il vetro, lontano dalle estremita' tonde. La frase della capsula e'
   * la stessa nei due fotogrammi, quindi non conta: resta solo quello che il vetro lascia passare. Il testo nitido sotto
   * un vetro limpido cambia di quasi 255; sotto un vetro smerigliato poco.
   */
  private fun throughGlass(a: Bitmap, b: Bitmap, area: Rect): Float {
    val values = ArrayList<Float>()
    for (y in max(0, area.top.toInt())..min(a.height - 1, area.bottom.toInt())) {
      for (x in max(0, area.left.toInt())..min(a.width - 1, area.right.toInt())) {
        values += kotlin.math.abs(luminance(a, x, y) - luminance(b, x, y))
      }
    }
    values.sort()
    return values[(values.size * 0.99f).toInt().coerceAtMost(values.size - 1)]
  }

  private fun capsuleGlass(inOverlay: Boolean) {
    val tag = if (inOverlay) "" else "-box"
    page(capsuleInOverlay = inOverlay)
    // La pagina nuda: la capsula in linea non c'e'. Due testi diversi nelle stesse righe.
    val bareA = capture("capsula-assente-a$tag")
    variant = 1
    advance(300)
    val bareB = capture("capsula-assente-b$tag")
    variant = 0
    advance(300)
    online = false
    advance(900)
    val outer = capsule()
    // Il vetro e' alto 36 dp al centro del bersaglio da 44; via le estremita' tonde (18 dp) e un margine.
    val area = Rect(outer.left + 18f * px, outer.center.y - 14f * px, outer.right - 18f * px, outer.center.y + 14f * px)
    val openA = capture("capsula-offline-a$tag")
    variant = 1
    advance(300)
    val openB = capture("capsula-offline-b$tag")
    variant = 0
    val bare = throughGlass(bareA, bareB, area)
    val through = throughGlass(openA, openB, area)
    val name = "capsula${if (inOverlay) " nell'overlay" else " in un Box"}"
    println("F8p20b $name: la pagina nuda cambia di ${"%.0f".format(bare)}, attraverso il vetro di ${"%.0f".format(through)}, area $area")
    assertTrue("$name: sotto la capsula la pagina non cambia abbastanza: $bare", bare > 150f)
    assertTrue("$name: attraverso il vetro il testo si legge (differenza $through)", through < SPREAD_LIMIT)
  }

  @Test fun `la capsula nell'overlay smeriglia il testo sotto`() = capsuleGlass(inOverlay = true)

  @Test fun `la capsula di serie in un Box smeriglia il testo sotto`() = capsuleGlass(inOverlay = false)

  private companion object {
    /** La soglia di F8.8b: sopra 40 su 255 le lettere nere su bianco si leggono ancora. */
    const val SPREAD_LIMIT = 40f

    /** Solo per un giro di indagine: il registro del vetro nell'uscita della prova. */
    const val GlassLogForDebug = false
  }
}
