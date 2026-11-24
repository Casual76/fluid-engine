package dev.antigravity.fluidengine.sample.novita

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.foundation.FluidWelcomeKind
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidLaunchOrigin
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeContent
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeFeature
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomePage
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeSheet
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidWelcomeState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.11 · Il benvenuto e le novità in un albero Compose vero: una volta per versione, mai da un link o da una notifica, mai
 * sopra un'azione in corso; i passi coi pallini; il testo grande col tasto fermo in basso; le righe che entrano una dopo
 * l'altra (dai fotogrammi); TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class F8p11WelcomeUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val features = listOf(
    FluidWelcomeFeature(FluidIcons.Edit, "Scrivi a modo tuo", "Elenchi, titoli e citazioni."),
    FluidWelcomeFeature(FluidIcons.Folder, "Tutto in ordine", "Quaderni e cartelle."),
    FluidWelcomeFeature(FluidIcons.Search, "Trova al volo", "Cerca mentre scrivi."),
    FluidWelcomeFeature(FluidIcons.Lock, "Note riservate", "Chiudi le note private."),
  )

  @Composable
  private fun Hosted(memory: FluidFirstUseMemory, content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host, LocalFluidFirstUseMemory provides memory) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private fun settle() {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
  }

  @Test fun unaVoltaPerVersione() {
    val memory = FluidFirstUseMemory.inMemory()
    var version by mutableStateOf("1.0.0")
    var news by mutableStateOf(false)
    var launch by mutableIntStateOf(0)
    lateinit var state: FluidWelcomeState
    compose.setContent {
      Hosted(memory) {
        key(launch, version) {
          state = rememberFluidWelcomeState(version, hasNews = news, freshInstall = launch == 0)
          FluidWelcomeSheet(state.isShowing, state::dismiss, if (state.kind == FluidWelcomeKind.WhatsNew) "Novità di Note" else "Benvenuto in Note", features)
        }
      }
    }
    settle()
    assertEquals(FluidWelcomeKind.Welcome, state.kind)
    assertTrue(state.isShowing)
    compose.onNodeWithText("Benvenuto in Note").assertExists()
    // Segnata come vista già quando compare: chi chiude l'app a foglio aperto non lo rivede.
    assertEquals("1.0.0", runBlocking { memory.read(FluidFirstUseKeys.welcomeSeen()) })
    compose.onNodeWithText("Continua").performClick()
    settle()
    assertFalse(state.isShowing)

    // Riaperta, la stessa versione: niente.
    launch = 1
    settle()
    assertTrue(state.isDecided)
    assertNull(state.kind)
    compose.onNodeWithText("Benvenuto in Note").assertDoesNotExist()

    // Un aggiornamento senza novità: niente, ma la versione si segna.
    version = "1.1.0"
    settle()
    assertNull(state.kind)
    assertEquals("1.1.0", runBlocking { memory.read(FluidFirstUseKeys.welcomeSeen()) })

    // Un aggiornamento con le novità: il foglio «Novità di…», una volta.
    news = true
    version = "1.2.0"
    settle()
    assertEquals(FluidWelcomeKind.WhatsNew, state.kind)
    assertTrue(state.isShowing)
    compose.onNodeWithText("Novità di Note").assertExists()
    compose.onNodeWithText("Continua").performClick()
    settle()
    launch = 2
    settle()
    assertNull(state.kind)
  }

  @Test fun maiDaUnLinkNeSopraUnAzioneInCorso() {
    val memory = FluidFirstUseMemory.inMemory()
    var suppressed by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var launch by mutableIntStateOf(0)
    lateinit var state: FluidWelcomeState
    compose.setContent {
      Hosted(memory) {
        key(launch) {
          state = rememberFluidWelcomeState("2.0.0", hasNews = true, suppressed = suppressed, busy = busy, freshInstall = false)
          FluidWelcomeSheet(state.isShowing, state::dismiss, "Novità di Note", features)
        }
      }
    }
    settle()
    // Aperta da un link: deciso, ma non mostrato, e la versione resta da vedere.
    assertEquals(FluidWelcomeKind.WhatsNew, state.kind)
    assertFalse(state.isShowing)
    assertNull(runBlocking { memory.read(FluidFirstUseKeys.welcomeSeen()) })

    // L'apertura dopo, dall'icona, con un'azione in corso: aspetta.
    suppressed = false
    busy = true
    launch = 1
    settle()
    assertFalse(state.isShowing)
    busy = false
    settle()
    assertTrue(state.isShowing)
    assertEquals("2.0.0", runBlocking { memory.read(FluidFirstUseKeys.welcomeSeen()) })

    // Il riconoscimento dell'apertura.
    assertTrue(FluidLaunchOrigin.isDirect(Intent(Intent.ACTION_VIEW, Uri.parse("fluidglass://nota/1"))))
    assertTrue(FluidLaunchOrigin.isDirect(Intent().putExtra(FluidLaunchOrigin.ExtraFromNotification, true)))
    assertFalse(FluidLaunchOrigin.isDirect(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
  }

  @Test fun passiCoiPallini() {
    var finished = 0
    val pages = listOf(
      FluidWelcomePage("Benvenuto in Note", features.take(2)),
      FluidWelcomePage("Resta aggiornato", features.drop(2).take(1)),
      FluidWelcomePage("Tutto pronto", features.takeLast(1), actionLabel = "Inizia"),
    )
    compose.setContent {
      Hosted(FluidFirstUseMemory.inMemory()) {
        FluidWelcomeContent(pages = pages, onFinish = { finished++ }, modifier = Modifier.fillMaxSize())
      }
    }
    settle()
    // I pallini dicono il passo a TalkBack.
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Passo 1 di 3")), useUnmergedTree = true)
      .assertExists()
    compose.onNodeWithText("Continua").performClick()
    settle()
    compose.onNodeWithText("Resta aggiornato").assertIsDisplayed()
    // Col dito: nitido, segue il pager.
    compose.onRoot().performTouchInput { swipeLeft() }
    settle()
    compose.onNodeWithText("Tutto pronto").assertIsDisplayed()
    compose.onNodeWithText("Inizia").performClick()
    settle()
    assertEquals(1, finished)
  }

  @Config(qualifiers = "w360dp-h560dp-xhdpi")
  @Test fun colTestoGrandeIlTastoRestaFermoInBasso() {
    val pages = listOf(FluidWelcomePage("Benvenuto in Note", features))
    compose.setContent {
      Hosted(FluidFirstUseMemory.inMemory()) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
          FluidWelcomeContent(
            pages = pages, onFinish = {}, modifier = Modifier.fillMaxSize(),
            privacyNote = "Le tue note restano sul telefono.",
          )
        }
      }
    }
    settle()
    val root = compose.onRoot().getUnclippedBoundsInRoot()
    val button = compose.onNodeWithText("Continua").getUnclippedBoundsInRoot()
    // Il tasto sta per intero sullo schermo, in fondo (24 dp sopra il bordo).
    assertTrue("tasto in basso: $button su $root", button.bottom <= root.bottom && root.bottom - button.bottom < 40.dp)
    // L'ultima riga e la nota sulla privacy non ci stanno: si raggiungono scorrendo il corpo, e il tasto non si muove.
    val last = compose.onNodeWithText("Note riservate")
    assertTrue("l'ultima riga sotto il tasto: ${last.getUnclippedBoundsInRoot()} / $button", last.getUnclippedBoundsInRoot().bottom > button.top)
    compose.onNodeWithText("Le tue note restano sul telefono.").performScrollTo()
    settle()
    assertEquals(button, compose.onNodeWithText("Continua").getUnclippedBoundsInRoot())
  }

  @Test fun leRigheEntranoUnaDopoLAltra() {
    val pages = listOf(FluidWelcomePage("Benvenuto in Note", features))
    compose.mainClock.autoAdvance = false
    compose.setContent {
      Hosted(FluidFirstUseMemory.inMemory()) {
        FluidWelcomeContent(pages = pages, onFinish = {}, modifier = Modifier.fillMaxSize())
      }
    }
    compose.mainClock.advanceTimeByFrame()
    fun rowInk(time: Long): List<Float> {
      compose.mainClock.advanceTimeBy(time)
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      val image = capture("righe-$time")
      return features.map { feature ->
        val bounds = compose.onNodeWithText(feature.title).getUnclippedBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        ink(image, (bounds.left.value * density).toInt(), (bounds.top.value * density).toInt(),
          (bounds.right.value * density).toInt(), (bounds.bottom.value * density).toInt())
      }
    }
    // A 450 ms dalla prima composizione: la prima riga c'è già (entra a 250 ms), l'ultima ancora no (entra a 520).
    val early = rowInk(450)
    assertTrue("prima riga visibile: $early", early[0] > 0.02f)
    assertTrue("l'ultima non ancora: $early", early[3] < early[0] * 0.5f)
    // A un secondo tutte posate e a fuoco, coi titoli della stessa tinta.
    val late = rowInk(600)
    assertTrue("tutte posate: $late", late.all { it > 0.02f })
  }

  /**
   * La tavola contro Apple: il benvenuto nel formato di un iPhone (393 × 852 punti a 3×, come lo schermo di Password di
   * iOS 18 misurato), in chiaro e in scuro. Con `GLASS_F8P11_PIXELS` le immagini finiscono su disco per la tavola.
   */
  @Config(qualifiers = "w393dp-h852dp-xxhdpi")
  @Test fun tavolaNelFormatoTelefono() {
    var dark by mutableStateOf(false)
    compose.setContent {
      val mode = if (dark) dev.antigravity.fluidengine.foundation.ThemeMode.DARK else dev.antigravity.fluidengine.foundation.ThemeMode.LIGHT
      FluidTheme(settings = EngineSettings(themeMode = mode)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
          FluidWelcomeContent(
            pages = listOf(
              FluidWelcomePage("Benvenuto in Note", features.take(2), icon = FluidIcons.Edit),
              FluidWelcomePage(
                "Attiva le notifiche", icon = FluidIcons.Notifications,
                message = "Note ti avvisa quando qualcuno cambia un quaderno che condividi, e solo allora.",
              ),
            ),
            onFinish = {},
            privacyNote = "Le tue note restano sul telefono: Note non le manda a nessuno.",
            modifier = Modifier.fillMaxSize().padding(top = 47.dp),
          )
        }
      }
    }
    for (side in listOf("chiaro", "scuro")) {
      dark = side == "scuro"
      settle()
      compose.mainClock.advanceTimeBy(1_500)
      settle()
      val image = capture("tavola-$side")
      // La riga parte dalla colonna delle icone, a 40 dp dal bordo: icone centrate a 60, testo a 98, come in Password.
      val row = compose.onNodeWithText("Scrivi a modo tuo").getUnclippedBoundsInRoot()
      assertTrue("la colonna delle icone parte a 40 dp: ${row.left}", kotlin.math.abs(row.left.value - 40f) < 2f)
      assertTrue(image.width > 0)
    }
    // Il secondo passo, come «Turn On Notifications» di Password: icona, titolo, frase.
    compose.onNodeWithText("Continua").performClick()
    settle()
    compose.mainClock.advanceTimeBy(1_000)
    for (side in listOf("scuro", "chiaro")) {
      dark = side == "scuro"
      settle()
      compose.mainClock.advanceTimeBy(300)
      capture("tavola-passo2-$side")
    }
    compose.onNodeWithText("Attiva le notifiche").assertIsDisplayed()
  }

  private fun capture(label: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(window, image, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    System.getenv("GLASS_F8P11_PIXELS")?.let { path ->
      val dir = File(path).apply { mkdirs() }
      File(dir, "$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return image
  }

  /** Quanta parte del riquadro è inchiostro (lontana dal fondo dell'angolo in alto a sinistra della finestra). */
  private fun ink(image: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Float {
    val ground = image.getPixel(2, image.height - 2)
    var inked = 0
    var total = 0
    for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(image.height)) {
      for (x in left.coerceAtLeast(0) until right.coerceAtMost(image.width)) {
        total++
        val p = image.getPixel(x, y)
        val d = kotlin.math.abs(android.graphics.Color.red(p) - android.graphics.Color.red(ground)) +
          kotlin.math.abs(android.graphics.Color.green(p) - android.graphics.Color.green(ground)) +
          kotlin.math.abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(ground))
        if (d > 90) inked++
      }
    }
    return if (total == 0) 0f else inked.toFloat() / total
  }
}
