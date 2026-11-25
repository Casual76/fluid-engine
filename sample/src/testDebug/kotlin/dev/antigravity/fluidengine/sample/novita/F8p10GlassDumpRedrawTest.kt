package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * F8.10 · le prove di debug sullo stato fermo (`FluidGlassDiagnostics.stateDumpForTesting`) fanno ridisegnare davvero
 * quello che dicono, a foglio fermo e senza un tocco: `vetri` ogni vetro (senza catture nuove), `vetri-cattura` ogni vetro
 * con la cattura nuova, `tasto` il contenuto del tasto di vetro, `foglio` lo strato del contenuto del foglio.
 *
 * Prima passavano da un `invalidateDraw()` chiesto da fuori di un fotogramma, e sul tablet (25 novembre, finestra F) la
 * finestra rendeva due fotogrammi senza che nessun vetro si disegnasse: le prove non provavano niente. Adesso passano da
 * uno stato letto in disegno. Il foglio e' un [FluidGlassSheet] che nasce dal suo tasto, con una «x» di vetro in testata,
 * come il foglio di condivisione; la finestra si disegna a ogni passo (Robolectric da solo non disegna fra due catture).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p10GlassDumpRedrawTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var open by mutableStateOf(false)

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.enableStateDumpForTesting()
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.enabled = false
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      drawFrame()
      left -= 16
    }
  }

  private var frame: Bitmap? = null

  private fun drawFrame() {
    val window = compose.activity.window
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) return
    val image = frame ?: Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { frame = it }
    PixelCopy.request(window, image, {}, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        val page = rememberGlassBackdrop()
        val namespace = rememberFluidGlassNamespace()
        val source = remember(namespace) { FluidTransitionSource("condividi", namespace) }
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.White)) {
              Column {
                Box(Modifier.fillMaxWidth().height(360.dp).background(Color(0xFF3A2440)))
                repeat(20) {
                  BasicText("Posizione dell'originale, 43° 46′ N", style = TextStyle(fontSize = 17.sp, color = Color.Black))
                }
              }
            }
            FluidGlassIconButton(
              onClick = { open = true },
              size = 44.dp,
              modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 12.dp)
                .fluidTransitionSource("condividi", namespace, CircleShape),
            ) { FluidIcon(FluidIcons.IosShare, contentDescription = null) }
            FluidGlassSheet(
              visible = open,
              onDismissRequest = { open = false },
              detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
              source = source,
              undimmedUpTo = null,
            ) {
              Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(Color(0xFFD0D0D4)))
                BasicText("1 foto", Modifier.weight(1f).padding(start = 12.dp), style = TextStyle(fontSize = 17.sp))
                FluidGlassIconButton(onClick = { open = false }, size = 36.dp, modifier = Modifier.testTag(CloseTag)) {
                  FluidIcon(FluidIcons.Close, contentDescription = null, size = 18.dp)
                }
              }
              Spacer(Modifier.height(320.dp))
            }
            FluidGlassModalHost(state = host, backdrop = page)
          }
        }
      }
    }
    advance(400)
    open = true
    advance(2_000)
  }

  /** Le righe della fotografia di quel tipo, con la misura scritta. */
  private fun lines(kind: String, size: String? = null): List<String> =
    FluidGlassDiagnostics.stateDumpForTesting().filter { "kind=$kind " in it && (size == null || "Size($size)" in it) }

  private fun count(line: String, key: String): Int =
    Regex("$key=(\\d+)").find(line)?.groupValues?.get(1)?.toInt() ?: error("$key mancante in: $line")

  private fun field(line: String, key: String): String = Regex("$key=(\\S+)").find(line)?.groupValues?.get(1) ?: "?"

  /** Un vetro della fotografia: la sua identita' e la misura. */
  private fun glassKey(line: String): String = "${field(line, "id")} ${Regex("size=(Size\\([^)]*\\))").find(line)?.groupValues?.get(1)}"

  /** I disegni del vetro della «x» (72 px a xhdpi), dalla fotografia. */
  private fun closeGlassDraws(): Int {
    val glass = lines("glass", "72.0, 72.0")
    assertEquals("un solo vetro della «x» nella fotografia: $glass", 1, glass.size)
    return count(glass.single(), "draws")
  }

  /** Quante volte si e' registrato il contenuto di quel tipo (la sonda piu' attiva, se ce n'e' piu' d'una). */
  private fun records(kind: String, size: String? = null): Int {
    val found = lines(kind, size)
    assertTrue("nessuna sonda $kind${size?.let { " $it" } ?: ""} nella fotografia", found.isNotEmpty())
    return found.maxOf { count(it, "records") }
  }

  @Test fun eachDebugActionRedrawsWhatItSaysAtRest() {
    show()

    // A riposo, senza prove, il vetro della «x» non si ridisegna: e' il controllo.
    val restStart = closeGlassDraws()
    advance(300)
    val restEnd = closeGlassDraws()
    println("F8.10 prove: a riposo, disegni del vetro della «x» $restStart -> $restEnd")
    assertEquals("a riposo il vetro della «x» si ridisegna da solo: la prova non direbbe niente", restStart, restEnd)

    // `vetri`: il vetro si ridisegna; non chiede catture, ma un vetro che ne aveva un motivo e non si disegnava la rifa'
    // (lo scrive l'uscita della prova, vetro per vetro).
    val capturesBefore = FluidGlassDiagnostics.totalCaptures
    val glassBefore = lines("glass").associate { glassKey(it) to it }
    assertTrue(FluidGlassDiagnostics.stateDumpForTesting("vetri").single().startsWith("azione=vetri vetri="))
    advance(100)
    val afterGlass = closeGlassDraws()
    val capturesAfterGlass = FluidGlassDiagnostics.totalCaptures
    println("F8.10 prove: vetri, disegni $restEnd -> $afterGlass, catture $capturesBefore -> $capturesAfterGlass")
    for (line in lines("glass")) {
      val before = glassBefore[glassKey(line)] ?: continue
      println("F8.10 prove: vetri, ${glassKey(line)}: disegni ${count(before, "draws")} -> ${count(line, "draws")}, " +
        "cattura ${field(before, "captureAge")} -> ${field(line, "captureAge")}")
    }
    assertTrue("`vetri` non fa ridisegnare il vetro della «x» ($restEnd -> $afterGlass)", afterGlass > restEnd)

    // `vetri-cattura`: il vetro si ridisegna e rifa' la cattura.
    FluidGlassDiagnostics.stateDumpForTesting("vetri-cattura")
    advance(100)
    val afterCapture = closeGlassDraws()
    val capturesAfterCapture = FluidGlassDiagnostics.totalCaptures
    println("F8.10 prove: vetri-cattura, disegni $afterGlass -> $afterCapture, catture $capturesAfterGlass -> $capturesAfterCapture")
    assertTrue("`vetri-cattura` non fa ridisegnare il vetro della «x»", afterCapture > afterGlass)
    assertTrue("`vetri-cattura` non rifa' le catture", capturesAfterCapture > capturesAfterGlass)

    // `tasto`: il contenuto del tasto di vetro si registra di nuovo.
    val buttonBefore = records("button", "72.0, 72.0")
    FluidGlassDiagnostics.stateDumpForTesting("tasto")
    advance(100)
    val buttonAfter = records("button", "72.0, 72.0")
    println("F8.10 prove: tasto, registrazioni del contenuto $buttonBefore -> $buttonAfter")
    assertTrue("`tasto` non fa registrare il contenuto della «x» ($buttonBefore -> $buttonAfter)", buttonAfter > buttonBefore)

    // `foglio`: lo strato del contenuto del foglio si registra di nuovo.
    val sheetBefore = records("sheetLayer")
    FluidGlassDiagnostics.stateDumpForTesting("foglio")
    advance(100)
    val sheetAfter = records("sheetLayer")
    println("F8.10 prove: foglio, registrazioni dello strato $sheetBefore -> $sheetAfter")
    assertTrue("`foglio` non fa registrare lo strato del foglio ($sheetBefore -> $sheetAfter)", sheetAfter > sheetBefore)

    // La fotografia senza prova resta una fotografia.
    val dump = FluidGlassDiagnostics.stateDumpForTesting()
    assertNotNull(dump.lastOrNull { it.endsWith(" fine") })
  }

  /**
   * La correzione candidata (F8.10, `forceLayerDefaults`): il vetro su vetro (la «x», che campiona il materiale del foglio)
   * riscrive lo strato di posa quando si ferma dopo le catture prese durante lo zoom; gli altri vetri no.
   */
  @Test fun onlyTheNestedGlassRewritesItsPlacementLayerAtRest() {
    show()
    val glasses = lines("glass")
    for (line in glasses) println("F8.10 prove: a riposo, ${glassKey(line)}: settle=${field(line, "settle")} material=${field(line, "material")}")
    val close = lines("glass", "72.0, 72.0").single()
    assertTrue("la «x» non ha riscritto lo strato di posa a riposo: $close", count(close, "settle") >= 1)
    for (line in glasses) {
      if (field(line, "material") == "null") {
        assertEquals("un vetro non annidato ha riscritto lo strato di posa: $line", 0, count(line, "settle"))
      }
    }
    // Riscriverlo non cambia l'immagine: la «x» resta nitida.
    val ink = glyphInk(capture("riposo-con-correzione"))
    assertTrue("con la riscrittura la «x» non e' nitida ($ink)", ink < InkLimit)
  }

  @Test fun theLayerAndHideActionsDoWhatTheySayAtRest() {
    show()
    val sharp = glyphInk(capture("prima"))
    assertTrue("a riposo la «x» non e' nitida ($sharp)", sharp < InkLimit)

    // `posa-proprieta`: solo il blocco dello strato di posa, nessuna registrazione del contenuto.
    val posesProps = count(lines("glass", "72.0, 72.0").single(), "poses")
    FluidGlassDiagnostics.stateDumpForTesting("posa-proprieta")
    advance(100)
    val posesPropsAfter = count(lines("glass", "72.0, 72.0").single(), "poses")
    println("F8.10 prove: posa-proprieta, strato $posesProps -> $posesPropsAfter")
    assertTrue("`posa-proprieta` non rifa' lo strato di posa", posesPropsAfter > posesProps)

    // `posa`: lo strato di posa del vetro riscrive le proprieta' e registra di nuovo il contenuto. La dissolvenza del
    // contenuto (`contentFade`) c'e' solo col vetro liquido: qui, coi verdetti degli shader rifiutati, il vetro e' quello di
    // ripiego e la sonda non c'e' (sul tablet si'); allora conta solo lo strato.
    val glass = lines("glass", "72.0, 72.0").single()
    val posesBefore = count(glass, "poses")
    val contentBefore = lines("contentFade", "72.0, 72.0").maxOfOrNull { count(it, "records") }
    FluidGlassDiagnostics.stateDumpForTesting("posa")
    advance(100)
    val posesAfter = count(lines("glass", "72.0, 72.0").single(), "poses")
    val contentAfter = lines("contentFade", "72.0, 72.0").maxOfOrNull { count(it, "records") }
    println("F8.10 prove: posa, strato $posesBefore -> $posesAfter, contenuto $contentBefore -> $contentAfter")
    assertTrue("`posa` non rifa' lo strato di posa della «x» ($posesBefore -> $posesAfter)", posesAfter > posesBefore)
    if (contentBefore != null && contentAfter != null) {
      assertTrue("`posa` non registra di nuovo il contenuto ($contentBefore -> $contentAfter)", contentAfter > contentBefore)
    }
    val afterPose = glyphInk(capture("posa"))
    assertTrue("dopo `posa` la «x» non e' piu' nitida ($afterPose)", afterPose < InkLimit)

    // `nascondi-vetro`: il vetro della «x» non disegna se stesso, il segno resta.
    FluidGlassDiagnostics.stateDumpForTesting("nascondi-vetro")
    advance(100)
    val noGlass = glyphInk(capture("nascondi-vetro"))
    println("F8.10 prove: nascondi-vetro, segno $noGlass")
    assertTrue("con `nascondi-vetro` il segno della «x» sparisce ($noGlass)", noGlass < InkLimit)

    // `nascondi-tasto`: la «x» sparisce del tutto; `mostra` la rimette.
    FluidGlassDiagnostics.stateDumpForTesting("nascondi-tasto")
    advance(100)
    val noButton = glyphInk(capture("nascondi-tasto"))
    FluidGlassDiagnostics.stateDumpForTesting("mostra")
    advance(100)
    val back = glyphInk(capture("mostra"))
    println("F8.10 prove: nascondi-tasto, segno $noButton; mostra, segno $back")
    assertTrue("con `nascondi-tasto` la «x» resta ($noButton)", noButton > InkLimit)
    assertTrue("dopo `mostra` la «x» non torna ($back)", back < InkLimit)
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8-10-prove").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  /** Il pixel piu' scuro nel quadrato centrale della «x» (un terzo del riquadro del tocco): il segno. */
  private fun glyphInk(image: Bitmap): Float {
    val x = compose.onNodeWithTag(CloseTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val side = x.width / 3f
    val area = Rect(x.center.x - side / 2f, x.center.y - side / 2f, x.center.x + side / 2f, x.center.y + side / 2f)
    var darkest = 255f
    for (y in area.top.toInt()..area.bottom.toInt()) for (px in area.left.toInt()..area.right.toInt()) {
      val pixel = image.getPixel(px, y)
      val luminance = 0.2126f * android.graphics.Color.red(pixel) + 0.7152f * android.graphics.Color.green(pixel) +
        0.0722f * android.graphics.Color.blue(pixel)
      darkest = minOf(darkest, luminance)
    }
    return darkest
  }

  private companion object {
    const val CloseTag = "f8-10-prove-x"

    /** Il segno nitido e' nero (sotto 60 su 255); senza segno la zona resta sopra 110. */
    const val InkLimit = 110f
  }
}
