@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassMenuPanel
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidMenuContentMap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflation
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInkProbe
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInkReport
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLens
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSelection
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPanelLayout
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.runBlocking
import dev.antigravity.fluidengine.ui.fluid.FluidMenuCoordinates
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInkFrame
import dev.antigravity.fluidengine.ui.fluid.fluidMenuInkFrame
import dev.antigravity.fluidengine.ui.fluid.fluidMenuInkHaloPx
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildRestPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassContentLensEffect
import dev.antigravity.fluidengine.ui.glass.field.glassMenuLensOverflow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

/**
 * S4g-ter: il limite dello strato della lente delle voci non taglia niente, a ogni scala.
 *
 * Senza limite lo shader della lente faceva allocare a Skia un intermedio grande quanto la finestra divisa per la scala delle
 * voci, e sul Tab S9 la prima apertura del gruppo arrivava a ~1 s (bisezione F7.3k). Il limite ([fluidMenuInkFrame]) e' lo
 * strato delle voci intero, spostato nella finestra, piu' la sfocatura. Qui la struttura del pannello
 * (`FluidGlassMenuPanel`: il nodo con la mappa del viaggio, lo strato `inkLens` con la lente vera, le celle col loro layer)
 * su righe a strisce che attraversano il bordo del vetro, alle scale della nascita e del ritorno.
 *
 * Un limite che taglia toglie righe: pixel diversi di decine di livelli. Il confronto e' col limite allargato di 64 px della
 * finestra, che a ogni scala resta un intermedio finito; da 0,72 in su anche con lo strato senza limite. Senza limite, a scala
 * 0,04, l'intermedio sarebbe la finestra moltiplicata per 25, e non e' un riferimento: la candidata F7.3k cadeva li'.
 * Ammessi due livelli di differenza: il filtro ricampiona lo strato a partire dall'origine del suo intermedio (sul banco GPU
 * della F7.3k anche un limite di 2048 px dava 1-2 livelli su qualche pixel).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h640dp-mdpi")
class LenteVociLimiteTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** 0: senza limite; 1: il limite; 2: il limite allargato di 64 px della finestra. */
  private val mode = mutableStateOf(1)
  private val scale = mutableStateOf(1f)
  private val bounds = ArrayList<Rect>()

  // Il vetro e lo spostamento del viaggio, in pixel del pannello (come la candidata F7.3k).
  private val field = Rect(32.5f, 26.25f, 180.5f, 112.25f)
  private val move = Offset(-14.25f, 12.5f)

  private fun scene() {
    compose.setContent {
      val plan = remember { PhysicsRenderPlan().also { buildRestPlan(it, FluidForm.Slab(field, FluidCornerRadii.all(18f))) } }
      val lens = remember { GlassContentLensEffect() }
      val coordinates = remember { FluidMenuCoordinates() }
      val inkLens = rememberGraphicsLayer()
      Box(Modifier.fillMaxSize().background(Color.Blue), contentAlignment = Alignment.Center) {
        Box(Modifier.size(240.dp, 160.dp).onGloballyPositioned { coordinates.value = it }) {
          Box(
            Modifier
              .fillMaxSize()
              .graphicsLayer {
                translationX = move.x
                translationY = move.y
                scaleX = scale.value
                scaleY = scale.value
              }
              .drawWithContent {
                val q = scale.value
                val map = FluidMenuContentMap(move, q)
                val origin = move + Offset(size.width, size.height) * ((1f - q) / 2f)
                // Prima della prima misura non ci sono coordinate: lo strato intero, senza spostamento.
                val frame = fluidMenuInkFrame(coordinates.value, size, map, fluidMenuInkHaloPx(5f * this.density, q))
                  ?: FluidMenuInkFrame(Offset.Zero, Rect(Offset.Zero, size))
                inkLens.renderEffect = lens.effect(
                  plan, origin, q, this.density, 5f, 1f, clipInk = true, overflow = glassMenuLensOverflow(field, origin, size, q),
                  inkShift = frame.shift,
                )
                inkLens.translationX = frame.shift.x
                inkLens.translationY = frame.shift.y
                inkLens.record { this@drawWithContent.drawContent() }
                val limit = when (mode.value) {
                  0 -> null
                  1 -> frame.bounds
                  else -> frame.bounds.inflate(64f / q)
                }
                if (limit == null) {
                  drawLayer(inkLens)
                } else {
                  bounds += limit
                  clipRect(limit.left, limit.top, limit.right, limit.bottom) { drawLayer(inkLens) }
                }
              },
          ) {
            Box(
              Modifier.matchParentSize().graphicsLayer { }.drawBehind {
                drawRect(Color.White)
                for (y in 0 until size.height.toInt() step 8) drawRect(Color.Black, Offset(0f, y.toFloat()), Size(size.width, 3f))
              },
            )
          }
        }
      }
    }
  }

  @Test fun ilLimiteDelleVociNonTagliaNienteAOgniScala() {
    scene()
    for (q in listOf(0.04f, 0.19f, 0.72f, 1f, 1.35f)) {
      compose.runOnIdle { scale.value = q; mode.value = 1 }
      val limited = windowBitmap()
      compose.runOnIdle { mode.value = 2 }
      val wider = windowBitmap()
      val (pixels, most) = difference(limited, wider)
      println("TAVOLA limite scala=$q contro il limite allargato: $pixels pixel diversi, al piu' $most livelli")
      assertTrue("scala $q: le voci ci sono", colors(limited).count { it != Color.Blue.toArgb() } > 0)
      assertTrue("scala $q: il limite taglia ($pixels pixel, $most livelli)", most <= 2)
      if (q >= 0.72f) {
        compose.runOnIdle { mode.value = 0 }
        val free = windowBitmap()
        val (p0, m0) = difference(limited, free)
        println("TAVOLA limite scala=$q contro lo strato senza limite: $p0 pixel diversi, al piu' $m0 livelli")
        assertTrue("scala $q: diverso dallo strato senza limite ($p0 pixel, $m0 livelli)", m0 <= 2)
      }
    }
  }

  @Test fun ilLimiteEDellaMisuraDelPannelloNonDellaFinestra() {
    scene()
    for (q in listOf(0.04f, 0.19f, 0.72f, 1f, 1.35f)) {
      bounds.clear()
      compose.runOnIdle { scale.value = q; mode.value = 1 }
      windowBitmap()
      val limit = bounds.last()
      // Nello spazio dello strato (prima della sua scala) l'intermedio e' il limite: lo strato, 240 × 160 dp, piu' il margine.
      val node = with(compose.density) { Size(240.dp.toPx(), 160.dp.toPx()) }
      val margin = fluidMenuInkHaloPx(5f * compose.density.density, q) / q
      println("TAVOLA limite scala=$q ${"%.0f x %.0f".format(limit.width, limit.height)} nello strato di ${"%.0f x %.0f".format(node.width, node.height)}")
      assertTrue("scala $q: limite ${limit.width} di larghezza", limit.width <= node.width + 2f * margin + 2f)
      assertTrue("scala $q: limite ${limit.height} di altezza", limit.height <= node.height + 2f * margin + 2f)
    }
  }

  /**
   * Il pannello delle voci vero (`FluidGlassMenuPanel`) con la lente accesa disegna lo strato della lente nel limite, e il limite
   * e' della misura del nodo delle voci: se il ritaglio sparisce dal pannello, o torna la finestra, il test cade. Un menu dal
   * tasto a meta' nascita, con la lente a fuoco 0,3; senza goccia (la sagoma e' una lastra) e senza vetro sotto, perche' in
   * Robolectric il corpo del vetro non compila.
   */
  @Test fun ilPannelloDelleVociDisegnaLaLenteNelLimite() {
    val reports = ArrayList<FluidMenuInkReport>()
    FluidMenuInkProbe.onBounds = { reports += it }
    try {
      // In mdpi un dp e' un pixel: il pannello sotto il tasto, a destra della finestra di 480 × 640.
      val panel = Rect(240f, 300f, 460f, 470f)
      val rest = Rect(410f, 250f, 454f, 294f)
      val anchor = FluidMenuAnchor().apply {
        restBounds = rest
        pressedBounds = rest
      }
      val journey = FluidMenuJourney().apply {
        prepare(anchor, Offset.Zero, panel, 22f, FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
        retarget(1f)
      }
      val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
        density = 1f
        liquidAllowed = false
      }
      journey.install(physics)
      runBlocking {
        journey.birthClock.snapTo(80f)
        journey.progress.snapTo(0.6f)
        journey.focus.snapTo(0.3f)
      }
      val lens = FluidMenuLens(journey, physics, panel)
      val actions = listOf("Condividi", "Aggiungi ai preferiti", "Modifica", "Elimina").map { FluidContextAction(it, onClick = {}) }
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          Box(Modifier.fillMaxSize().background(Color.Blue)) {
            FluidGlassMenuPanel(
              actions = actions,
              selection = remember { FluidMenuSelection() },
              layout = fluidMenuPanelLayout(actions.map { it.startsSection }, 36f, 8f, 8f),
              panelMaterial = null,
              pageBackdrop = null,
              contentAlpha = { 1f },
              contentMap = { lens.contentMap() },
              onPick = {},
              modifier = Modifier.offset(panel.left.dp, panel.top.dp).size(panel.width.dp, panel.height.dp),
              lens = lens,
            )
          }
        }
      }
      windowBitmap()
      val scale = lens.contentMap().scale
      println("TAVOLA pannello: ${reports.size} disegni col limite, scala $scale, limite ${reports.lastOrNull()?.bounds}")
      assertTrue("il pannello disegna la lente senza limite", reports.isNotEmpty())
      for (r in reports) {
        val margin = 2f * fluidMenuInkHaloPx(5f, r.scale) / r.scale + 2f
        assertTrue(
          "scala ${r.scale}: limite ${r.bounds} per un nodo di ${r.node}",
          r.bounds.width <= r.node.width + margin && r.bounds.height <= r.node.height + margin,
        )
      }
    } finally {
      FluidMenuInkProbe.onBounds = null
    }
  }

  private fun difference(a: Bitmap, b: Bitmap): Pair<Int, Int> {
    val x = colors(a)
    val y = colors(b)
    var pixels = 0
    var most = 0
    for (i in x.indices) {
      if (x[i] == y[i]) continue
      pixels++
      for (shift in intArrayOf(0, 8, 16, 24)) {
        most = max(most, abs((x[i] shr shift and 0xff) - (y[i] shr shift and 0xff)))
      }
    }
    return pixels to most
  }

  private fun colors(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  }

  private fun windowBitmap(): Bitmap {
    compose.waitForIdle()
    val window = compose.activity.window
    val content = window.findViewById<View>(android.R.id.content)
    val location = IntArray(2)
    content.getLocationInWindow(location)
    val area = android.graphics.Rect(location[0], location[1], location[0] + content.width, location[1] + content.height)
    val bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread {
      PixelCopy.request(window, area, bitmap, { result = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("PixelCopy: $result", result == PixelCopy.SUCCESS)
    return bitmap
  }
}
