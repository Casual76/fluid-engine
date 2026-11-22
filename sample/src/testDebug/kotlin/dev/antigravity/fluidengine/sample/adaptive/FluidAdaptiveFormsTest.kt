package dev.antigravity.fluidengine.sample.adaptive

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.dynamic.SelectionMailScene
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
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
 * La navigazione adattiva in tutte le forme che deve servire (F6B.12, richiesta di Alessio del 22 novembre 2026: il
 * telefono è il target principale, il tablet la piattaforma di prova). In ogni forma la scena vera della Posta (tab bar,
 * rail, tab bar in alto o barra laterale; lista e dettaglio con le loro barre), con una barra di stato da 24 dp e una di
 * navigazione da 24 dp, e tre stati: a riposo, in selezione, e sul tablet in orizzontale anche con la barra laterale
 * aperta. In ognuno i riquadri della navigazione (le schede, il tasto del pannello), dei tasti delle barre delle pagine
 * e dei titoli **non si intersecano** fra loro: «le sovrapposizioni di elementi UI sono da evitare quanto più possibile».
 *
 * Le forme: telefono stretto a 360 e 412 dp in verticale e in orizzontale, pieghevole aperto (673 dp), Tab S9 in
 * verticale (753 dp) e in orizzontale (1205 dp), finestra divisa stretta (320 dp). Le immagini vanno in
 * `build/reports/f6b-12/forme/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidAdaptiveFormsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var root: View
  private val out = File("build/reports/f6b-12/forme")

  /** I tasti delle barre delle pagine, per nome. */
  private val barButtons = listOf("Indietro", "Seleziona", "Fine", "Seleziona tutto", "Deseleziona")

  /** I titoli: quello grande della lista, quello del dettaglio, il conteggio della selezione. */
  private val titles = listOf("Posta in arrivo", "Nessun messaggio", "0 selezionati", "1 selezionato")

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    out.mkdirs()
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    compose.waitForIdle()
  }

  private fun open() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        root = LocalView.current
        SelectionMailScene(onBack = {})
      }
    }
    advance(400)
    // Le barre di sistema di un telefono con i gesti: 24 dp sopra, 24 sotto (48 px a xhdpi).
    compose.runOnUiThread {
      val bars = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
        .setVisible(WindowInsetsCompat.Type.statusBars(), true)
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
        .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(root, bars)
    }
    advance(800)
  }

  private fun tap(name: String) {
    compose.onNodeWithContentDescription(name).performClick()
    Snapshot.sendApplyNotifications()
    advance(1600)
  }

  /** Un riquadro che si vede: non vuoto e dentro la finestra. */
  private fun SemanticsNode.box(): Rect? {
    val r = boundsInRoot
    return r.takeIf { it.width > 1f && it.height > 1f && it.right > 0f && it.bottom > 0f }
  }

  private fun nodes(matcher: SemanticsMatcher, merged: Boolean = false): List<SemanticsNode> =
    compose.onAllNodes(matcher, useUnmergedTree = !merged).fetchSemanticsNodes()

  /** Le destinazioni della Posta: le schede della tab bar, del rail, della capsula in alto o della barra laterale. */
  private val destinations = listOf("Posta", "Cartelle", "Cerca")

  /**
   * I riquadri, in px: la navigazione (le schede, cercate per nome nell'albero fuso, cioè tutta la scheda e non solo la
   * scritta; e il tasto del pannello) e gli elementi delle pagine.
   */
  private fun boxes(): Pair<List<Pair<String, Rect>>, List<Pair<String, Rect>>> {
    val navigation = destinations.flatMap { name ->
      nodes(hasText(name) or hasContentDescription(name), merged = true).mapNotNull { node -> node.box()?.let { "scheda $name" to it } }
    } + listOf("Pannello laterale", "Chiudi il pannello").flatMap { name ->
      nodes(hasContentDescription(name)).mapNotNull { node -> node.box()?.let { "tasto $name" to it } }
    }
    val page = barButtons.flatMap { name ->
      nodes(hasContentDescription(name)).mapNotNull { node -> node.box()?.let { "tasto $name" to it } }
    } + titles.flatMap { name ->
      // Solo le scritte che si vedono: l'area viva che annuncia il conteggio a TalkBack non ha disegno.
      nodes(hasText(name)).filter { it.config.getOrNull(SemanticsProperties.LiveRegion) == null }
        .mapNotNull { node -> node.box()?.let { "titolo $name" to it } }
    }
    return navigation.distinctBy { it.second } to page.distinctBy { it.second }
  }

  private fun shot(name: String) {
    val decor = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, decor.width, decor.height), bitmap, { result ->
        assertEquals(PixelCopy.SUCCESS, result)
      }, Handler(Looper.getMainLooper()))
    }
    File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    // Le bitmap del tablet sono grandi (2410 × 1506): la suite gira in un processo solo, non si tengono.
    bitmap.recycle()
  }

  /** Due riquadri si intersecano se si coprono per più di mezzo pixel nei due versi (toccarsi va bene). */
  private fun Rect.crosses(other: Rect) =
    minOf(right, other.right) - maxOf(left, other.left) > .5f && minOf(bottom, other.bottom) - maxOf(top, other.top) > .5f

  /** Nessuna coppia si interseca: navigazione con pagina, e gli elementi delle pagine fra loro. */
  private fun check(form: String, state: String, picture: Boolean = true) {
    if (picture) shot("$form-$state")
    val (navigation, page) = boxes()
    // In selezione, sul telefono, la tab bar diventa le azioni: le schede non ci sono, e va bene.
    if (state != "selezione") assertTrue("$form, $state: la navigazione c'è", navigation.isNotEmpty())
    assertTrue("$form, $state: le barre delle pagine ci sono", page.isNotEmpty())
    val clashes = buildList {
      for ((a, ra) in navigation) for ((b, rb) in page) if (ra.crosses(rb)) add("$a $ra × $b $rb")
      for (i in page.indices) for (j in i + 1 until page.size) {
        val (a, ra) = page[i]
        val (b, rb) = page[j]
        // Lo stesso nome è lo stesso elemento visto da due nodi annidati (la scritta e il suo contenitore).
        if (a != b && ra.crosses(rb)) add("$a $ra × $b $rb")
      }
    }
    assertTrue("$form, $state: ${clashes.joinToString("; ")}", clashes.isEmpty())
  }

  /** A riposo e in selezione (il tasto «Seleziona», poi «Fine»). */
  private fun restAndSelection(form: String) {
    open()
    check(form, "riposo")
    tap("Seleziona")
    check(form, "selezione")
    tap("Fine")
    check(form, "di-nuovo-a-riposo", picture = false)
  }

  @Test @Config(qualifiers = "w360dp-h800dp-port-xhdpi")
  fun `telefono a 360 dp in verticale`() = restAndSelection("telefono-360-verticale")

  @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
  fun `telefono a 360 dp in orizzontale`() = restAndSelection("telefono-360-orizzontale")

  @Test @Config(qualifiers = "w412dp-h915dp-port-xhdpi")
  fun `telefono a 412 dp in verticale`() = restAndSelection("telefono-412-verticale")

  @Test @Config(qualifiers = "w915dp-h412dp-land-xhdpi")
  fun `telefono a 412 dp in orizzontale`() = restAndSelection("telefono-412-orizzontale")

  @Test @Config(qualifiers = "w673dp-h841dp-port-xhdpi")
  fun `pieghevole aperto`() = restAndSelection("pieghevole-673")

  @Test @Config(qualifiers = "w753dp-h1205dp-port-xhdpi")
  fun `tablet in verticale`() = restAndSelection("tablet-verticale")

  @Test @Config(qualifiers = "w1205dp-h753dp-land-xhdpi")
  fun `tablet in orizzontale, con la tab bar in alto`() = restAndSelection("tablet-orizzontale")

  @Test @Config(qualifiers = "w1205dp-h753dp-land-xhdpi")
  fun `tablet in orizzontale, con la barra laterale aperta`() {
    open()
    tap("Pannello laterale")
    check("tablet-orizzontale-barra-laterale", "riposo")
    tap("Seleziona")
    check("tablet-orizzontale-barra-laterale", "selezione")
    // Di nuovo la tab bar in alto: la fascia torna, e le barre delle pagine con lei.
    tap("Fine")
    tap("Chiudi il pannello")
    check("tablet-orizzontale-barra-laterale", "richiusa")
  }

  @Test @Config(qualifiers = "w320dp-h753dp-port-xhdpi")
  fun `finestra divisa stretta`() = restAndSelection("finestra-divisa-320")
}
