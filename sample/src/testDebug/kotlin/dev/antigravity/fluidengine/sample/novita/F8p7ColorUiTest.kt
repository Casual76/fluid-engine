package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidColorEyedropper
import dev.antigravity.fluidengine.ui.fluid.FluidColorGrid
import dev.antigravity.fluidengine.ui.fluid.FluidColorPicker
import dev.antigravity.fluidengine.ui.fluid.FluidColorPickerMode
import dev.antigravity.fluidengine.ui.fluid.FluidColorPickerTags
import dev.antigravity.fluidengine.ui.fluid.FluidColorRow
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRecentColors
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidColorHex
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidContentColors
import dev.antigravity.fluidengine.ui.theme.FluidContentSide
import dev.antigravity.fluidengine.ui.theme.FluidContentTheme
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.LocalFluidContentRoles
import dev.antigravity.fluidengine.ui.theme.fluidContentRoles
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

/**
 * F8.7 · Il selettore di colore e il tema tinto dal contenuto, in Robolectric: il selettore cambia modo, legge e scrive
 * l'esadecimale, salva i recenti; il contagocce prende il pixel giusto della finestra (PixelCopy vero); il tema della
 * pagina entra ed esce sfumando, e col movimento ridotto subito. Col vetro vero (verdetti degli shader accettati).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p7ColorUiTest {

  @get:Rule
  val compose = createComposeRule()

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
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

  private lateinit var view: View

  private fun show(reduced: Boolean = false, dark: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
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
    advance(64)
  }

  private fun segment(label: String) =
    compose.onAllNodes(hasText(label) and hasClickAction()).onFirst()

  // --- Il selettore --------------------------------------------------------------------------------------------------

  @Test
  fun `il selettore cambia modo e la griglia sceglie la casella sotto il dito`() {
    var color by mutableStateOf(Color(0xFFFF3B30))
    show {
      FluidColorPicker(color = color, onColorChange = { color = it }, modifier = Modifier.fillMaxWidth())
    }
    compose.onNodeWithTag(FluidColorPickerTags.Grid).assertExists()
    // La casella della quinta riga di colore, seconda colonna: il blu pieno di iOS.
    compose.onNodeWithTag(FluidColorPickerTags.Grid).performTouchInput {
      click(Offset(width * (1.5f / 12f), height * (5.5f / 10f)))
    }
    advance(64)
    assertEquals(0xFF0062FE.toInt(), color.toArgb())

    segment("Spettro").performClick()
    advance(400)
    compose.onNodeWithTag(FluidColorPickerTags.Spectrum).assertExists()
    compose.onNodeWithTag(FluidColorPickerTags.Grid).assertDoesNotExist()
    // In alto a sinistra dello spettro: il bianco; a meta' altezza a sinistra: il rosso pieno.
    compose.onNodeWithTag(FluidColorPickerTags.Spectrum).performTouchInput { click(Offset(1f, height * 0.5f)) }
    advance(64)
    assertTrue("rosso: $color", color.red > 0.97f && color.green < 0.05f && color.blue < 0.05f)

    segment("Cursori").performClick()
    advance(400)
    compose.onNodeWithTag(FluidColorPickerTags.Hex).assertExists()
    compose.onNodeWithTag(FluidColorPickerTags.slider("r")).assertExists()
  }

  @Test
  fun `i cursori leggono e scrivono l'esadecimale e l'opacita' resta`() {
    var color by mutableStateOf(Color(0xFF007AFF).copy(alpha = 0.5f))
    show {
      FluidColorPicker(
        color = color,
        onColorChange = { color = it },
        modifier = Modifier.fillMaxWidth(),
        initialMode = FluidColorPickerMode.Sliders,
      )
    }
    // Legge: il campo mostra il colore di adesso.
    val field = hasSetTextAction() and (hasTestTag(FluidColorPickerTags.Hex) or hasAnyAncestor(hasTestTag(FluidColorPickerTags.Hex)))
    compose.onNode(field and hasText("007AFF", substring = true)).assertExists()
    // Scrive: sei cifre valide cambiano il colore, con la sua opacita'.
    compose.onNode(field).performTextReplacement("34c759")
    advance(64)
    assertEquals("#34C759", fluidColorHex(color))
    assertEquals(0.5f, color.alpha, 0.01f)
    // Tre cifre non bastano: il colore non cambia finche' il codice non e' intero.
    compose.onNode(field).performTextReplacement("FF0")
    advance(64)
    assertEquals("#34C759", fluidColorHex(color))
    // Il cursore del rosso: in fondo a destra e' 255.
    compose.onNodeWithTag(FluidColorPickerTags.slider("r")).performTouchInput { click(Offset(width - 1f, height / 2f)) }
    advance(64)
    assertEquals("#FFC759", fluidColorHex(color))
    // L'opacita': a sinistra e' zero.
    compose.onNodeWithTag(FluidColorPickerTags.Opacity).performTouchInput { click(Offset(1f, height / 2f)) }
    advance(64)
    assertEquals(0f, color.alpha, 0.01f)
    assertEquals("#FFC759", fluidColorHex(color))
  }

  @Test
  fun `il piu' salva il colore nei recenti e il foglio ci mette quello scelto quando si chiude`() {
    val recents = FluidRecentColors()
    var color by mutableStateOf(Color(0xFFFF9500))
    show {
      Column(Modifier.fillMaxSize()) {
        FluidColorPicker(color = color, onColorChange = { color = it }, recents = recents, modifier = Modifier.weight(1f))
      }
    }
    compose.onNodeWithTag(FluidColorPickerTags.AddRecent).performClick()
    advance(64)
    assertEquals(listOf(Color(0xFFFF9500).toArgb()), recents.colors.map { it.toArgb() })
    // Un recente toccato torna il colore scelto.
    color = Color.Black
    advance(64)
    compose.onNode(
      hasContentDescription("arancione") and hasClickAction() and hasAnyAncestor(hasTestTag(FluidColorPickerTags.Recents)),
    ).performClick()
    advance(64)
    assertEquals(Color(0xFFFF9500).toArgb(), color.toArgb())
  }

  @Test
  fun `la riga apre il foglio dal pozzetto e alla chiusura il colore nuovo entra nei recenti`() {
    val recents = FluidRecentColors()
    var color by mutableStateOf(Color(0xFF34C759))
    show {
      FluidColorRow(title = "Sfondo", color = color, onColorChange = { color = it }, recents = recents)
    }
    compose.onNode(hasText("Sfondo") and hasClickAction()).performClick()
    advance(700)
    compose.onNodeWithTag(FluidColorPickerTags.Grid).assertExists()
    compose.onNodeWithTag(FluidColorPickerTags.Grid).performTouchInput {
      click(Offset(width * (1.5f / 12f), height * (5.5f / 10f)))
    }
    advance(64)
    assertEquals(FluidColorGrid[5 * 12 + 1].toArgb(), color.toArgb())
    compose.onNodeWithTag(FluidColorPickerTags.Close).performClick()
    advance(900)
    compose.onNodeWithTag(FluidColorPickerTags.Grid).assertDoesNotExist()
    assertEquals(listOf(FluidColorGrid[5 * 12 + 1].toArgb()), recents.colors.map { it.toArgb() })
  }

  // --- Il contagocce ------------------------------------------------------------------------------------------------

  @Test
  fun `il contagocce prende il pixel sotto il centro della lente`() {
    var picked: Color? = null
    var done = false
    var active by mutableStateOf(false)
    show {
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
          Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFE53935)))
          Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFF1E88E5)))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
          Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFF43A047)))
          Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFFDD835)))
        }
      }
      FluidColorEyedropper(active = active, onPick = { picked = it; done = true; active = false })
    }
    advance(200)
    active = true
    // La finestra si fotografa dopo che il foglio e' uscito (420 ms), poi compare la lente al centro.
    advance(600)
    compose.onNodeWithTag(FluidColorPickerTags.Loupe).assertExists()
    // Il dito porta la lente di un quarto a sinistra e un quarto in su: sul rosso.
    compose.onNodeWithTag(FluidColorPickerTags.Loupe).performTouchInput {
      val start = center
      down(start)
      moveBy(Offset(-width / 4f, -height / 4f))
      up()
    }
    advance(64)
    assertTrue("scelto", done)
    assertEquals(Color(0xFFE53935).toArgb(), picked!!.toArgb())

    // Di nuovo, verso il giallo in basso a destra.
    done = false
    active = true
    advance(600)
    compose.onNodeWithTag(FluidColorPickerTags.Loupe).performTouchInput {
      down(center)
      moveBy(Offset(width / 4f, height / 4f))
      up()
    }
    advance(64)
    assertEquals(Color(0xFFFDD835).toArgb(), picked!!.toArgb())
  }

  // --- Il tema della pagina ---------------------------------------------------------------------------------------

  private val orange = Color(0xFFF07A1A)
  private val colors = FluidContentColors(orange, orange, orange, listOf(orange), monochrome = false)

  @Test
  fun `il tema locale entra sfumando, cambia tinta ed esce tornando al tema normale`() {
    var on by mutableStateOf(false)
    var seed by mutableStateOf(colors)
    var background = Color.Unspecified
    var base = Color.Unspecified
    var roles: Any? = null
    show {
      base = MaterialTheme.colorScheme.background
      FluidContentTheme(colors = if (on) seed else null) {
        val bg = MaterialTheme.colorScheme.background
        val r = LocalFluidContentRoles.current
        SideEffect {
          background = bg
          roles = r
        }
      }
    }
    assertEquals(base, background)
    assertNull(roles)
    val target = fluidContentRoles(colors, isDark = false, side = FluidContentSide.Content)

    on = true
    advance(160)
    assertNotEquals("a meta' non e' ancora arrivato", target.pageTint, background)
    assertNotEquals("a meta' non e' piu' il tema normale", base, background)
    advance(600)
    assertEquals(target.pageTint, background)
    assertNotNull(roles)

    // Un'altra copertina: la tinta passa all'altra, senza tornare al bianco.
    val blue = Color(0xFF1E4FD8)
    seed = FluidContentColors(blue, blue, blue, listOf(blue), monochrome = false)
    advance(160)
    assertNotEquals(base, background)
    advance(600)
    assertEquals(fluidContentRoles(seed, isDark = false, side = FluidContentSide.Content).pageTint, background)

    // Spento: si torna al tema normale.
    on = false
    advance(800)
    assertEquals(base, background)
    assertNull(roles)
  }

  @Test
  fun `col movimento ridotto la tinta entra ed esce subito`() {
    var on by mutableStateOf(false)
    var background = Color.Unspecified
    var base = Color.Unspecified
    show(reduced = true) {
      base = MaterialTheme.colorScheme.background
      FluidContentTheme(colors = if (on) colors else null) {
        val bg = MaterialTheme.colorScheme.background
        SideEffect { background = bg }
      }
    }
    on = true
    advance(48)
    assertEquals(fluidContentRoles(colors, isDark = false, side = FluidContentSide.Content).pageTint, background)
    on = false
    advance(48)
    assertEquals(base, background)
  }

  // --- I fotogrammi, per guardarli ------------------------------------------------------------------------------------

  /** I pixel della finestra, col disegno vero (PixelCopy). */
  private fun snapshot(): Bitmap {
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()
    val window = activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    return image
  }

  private fun save(name: String) {
    val dir = File("build/f8p7-fotogrammi").apply { mkdirs() }
    FileOutputStream(File(dir, "$name.png")).use { snapshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  /** Lascia finire la lettura della copertina, che gira su un altro thread. */
  private fun settle(ms: Long) {
    var left = ms
    while (left > 0) {
      Thread.sleep(20)
      advance(48)
      left -= 48
    }
  }

  @Test
  fun `i fotogrammi delle scene, in chiaro e in scuro`() {
    var scene by mutableStateOf("pagina-brano")
    var dark by mutableStateOf(false)
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            key(scene, dark) { NewElementsPageAt(onBack = {}, initial = scene) }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    for (d in listOf(false, true)) {
      val side = if (d) "scuro" else "chiaro"
      dark = d
      // La pagina del brano, con la copertina letta davvero (decodifica e estrazione fuori dal thread principale).
      scene = "pagina-brano"
      settle(1500)
      save("brano-0-$side")
      compose.onNodeWithTag(F8p7Tags.cover(1)).performClick()
      settle(1500)
      save("brano-1-$side")
      compose.onNodeWithTag(F8p7Tags.cover(2)).performClick()
      settle(1500)
      save("brano-2-$side")

      // Il widget e il foglio dal pozzetto, nei tre modi.
      scene = "colore-widget"
      settle(600)
      save("widget-$side")
      compose.onNode(hasText("Sfondo") and hasClickAction()).performClick()
      advance(800)
      save("foglio-griglia-$side")
      segment("Spettro").performClick()
      advance(500)
      save("foglio-spettro-$side")
      segment("Cursori").performClick()
      advance(500)
      save("foglio-cursori-$side")
      compose.onNodeWithTag(FluidColorPickerTags.Close).performClick()
      advance(800)

      scene = "colore-materia"
      settle(600)
      save("materia-$side")
    }
  }
}
