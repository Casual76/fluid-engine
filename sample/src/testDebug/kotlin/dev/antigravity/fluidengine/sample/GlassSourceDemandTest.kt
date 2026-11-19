@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderNode
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberCombinedGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberSheetScene
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.pageSourceOrSelf
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.scrimBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class GlassSourceDemandTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val previousProbe = FluidGlassProbeDiagnostics.readerForTesting

  @After fun restoreProbe() { FluidGlassProbeDiagnostics.readerForTesting = previousProbe }

  // Con il solo layerBackdrop sul produttore la revisione conta esattamente le record eseguite.
  private fun GlassBackdropState.layer() = checkNotNull(layerBackdrop)
  private fun records(layer: LayerBackdrop) = layer.revision

  private fun frame(inspect: (Bitmap) -> Unit = {}) {
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      try {
        view.draw(Canvas(bitmap))
        inspect(bitmap)
      } finally {
        bitmap.recycle()
      }
    }
  }

  private fun modalFrame() = compose.runOnIdle {
    val view = compose.activity.window.decorView
    val window = RenderNode("F7.5")
    window.setPosition(0, 0, view.width, view.height)
    try {
      val canvas = window.beginRecording()
      try { view.draw(canvas) } finally { window.endRecording() }
    } finally {
      window.discardDisplayList()
    }
  }

  @Composable
  private fun Reader(source: Backdrop, modifier: Modifier = Modifier, beforeReplay: () -> Unit = {}) {
    Box(modifier.size(40.dp).drawPlainBackdrop(
      source, { RectangleShape }, {}, onDrawBehind = {
        beforeReplay()
        drawRect(Color.Black)
      },
    ))
  }

  @Test fun pageAndBarOnlyRecordWhileAtLeastOneReaderIsAttached() {
    val count = mutableStateOf(0)
    lateinit var layer: LayerBackdrop
    compose.setContent {
      layer = rememberGlassBackdrop().layer()
      Box {
        Box(Modifier.size(100.dp).layerBackdrop(layer, frozen = { true }).background(Color.Red))
        repeat(count.value) { Reader(layer) }
      }
    }
    frame()
    assertEquals(0L, records(layer))
    count.value = 2
    frame()
    assertEquals(1L, records(layer))
    val first = checkNotNull(layer.pageSourceOrSelf()).graphicsLayer
    count.value = 1
    frame()
    assertTrue(layer.isReadyToSample())
    assertFalse(first.isReleased)
    assertEquals(1L, records(layer))
    count.value = 0
    frame()
    assertFalse(layer.isReadyToSample())
    assertTrue(first.isReleased)
    assertEquals(1L, records(layer))
    count.value = 1
    frame()
    assertEquals(2L, records(layer))
    assertTrue(layer.pageSourceOrSelf()?.graphicsLayer !== first)
  }

  @Test fun firstVisibleFrameAfterReopeningSamplesTheNewColourEvenWhenFrozen() {
    val shown = mutableStateOf(false)
    val colour = mutableStateOf(Color.Red)
    var x = 0
    var y = 0
    compose.setContent {
      val state = rememberGlassBackdrop()
      Box {
        Box(Modifier.size(100.dp).graphicsLayer().glassBackdropSource(state) { true }.background(colour.value))
        if (shown.value) Reader(state.layer(), Modifier.onGloballyPositioned {
          val origin = it.positionInWindow()
          x = origin.x.toInt() + it.size.width / 2
          y = origin.y.toInt() + it.size.height / 2
        }) {
          assertTrue("la cattura è pronta già al disegno che la riproduce", state.layer().isReadyToSample())
        }
      }
    }
    frame()
    shown.value = true
    frame { assertEquals(Color.Red.toArgb(), it.getPixel(x, y)) }
    shown.value = false
    colour.value = Color.Green
    frame()
    shown.value = true
    frame { assertEquals(Color.Green.toArgb(), it.getPixel(x, y)) }
  }

  @Test fun aProbeWithoutGlassAlsoWakesAndReleasesTheSource() {
    FluidGlassProbeDiagnostics.readerForTesting = { Color.Red }
    val active = mutableStateOf(false)
    lateinit var layer: LayerBackdrop
    compose.setContent {
      layer = rememberGlassBackdrop().layer()
      Box {
        Box(Modifier.size(100.dp).layerBackdrop(layer, frozen = { true }).background(Color.Red))
        if (active.value) Box(Modifier.size(32.dp).then(remember { FluidGlassProbeDiagnostics.probe(layer) {} }))
      }
    }
    frame()
    assertEquals(0L, records(layer))
    active.value = true
    frame()
    assertEquals(1L, records(layer))
    active.value = false
    frame()
    assertFalse(layer.isReadyToSample())
    assertEquals(1L, records(layer))
  }

  @Test fun modalOpenAndClosed() = presentation(sheet = false)
  @Test fun detentedSheetOpenAndClosed() = presentation(sheet = true)

  @Test fun sheetSceneRecordsBothInputsOnItsFirstDrawAndReleasesItsTexture() {
    val visible = mutableStateOf(false)
    lateinit var page: LayerBackdrop
    lateinit var scrim: LayerBackdrop
    lateinit var scene: LayerBackdrop
    compose.setContent {
      val state = rememberGlassBackdrop()
      val overlay = rememberGlassBackdrop()
      page = state.layer()
      scrim = overlay.layer()
      val sheet = rememberSheetScene(rememberCombinedGlassBackdrop(state, overlay))
      sheet.backdrop.backdrop.forEachPart {
        if (it is LayerBackdrop && it !== page && it !== scrim) scene = it
      }
      Box(Modifier.size(96.dp)) {
        Box(Modifier.fillMaxSize().layerBackdrop(page, frozen = { true }).background(Color.Green))
        Box(Modifier.fillMaxSize().scrimBackdrop(scrim).background(Color.Black.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxSize().then(sheet.source))
        if (visible.value) Reader(sheet.backdrop.backdrop)
      }
    }
    frame()
    assertEquals(0L, records(page))
    assertEquals(0L, records(scrim))
    assertEquals(0L, records(scene))
    visible.value = true
    frame()
    assertEquals(1L, records(page))
    val scrimReads = records(scrim)
    assertTrue(scrimReads > 0L)
    val withReader = records(scene)
    assertTrue(withReader > 0L)
    val texture = checkNotNull(scene.pageSourceOrSelf()).graphicsLayer
    visible.value = false
    frame()
    assertTrue(texture.isReleased)
    assertFalse(page.isReadyToSample())
    assertFalse(scrim.isReadyToSample())
    assertEquals(scrimReads, records(scrim))
    assertEquals(withReader, records(scene))
  }

  @Test fun appOwnedRawLayerCanStillBeReadDirectly() {
    lateinit var raw: LayerBackdrop
    compose.setContent {
      raw = rememberLayerBackdrop()
      Box(Modifier.size(40.dp).layerBackdrop(raw).background(Color.Green))
    }
    frame()
    assertTrue(records(raw) > 0L)
    assertTrue(raw.graphicsLayer.size.width > 0)
  }

  @Test fun overlappingPageProducersRetireAndRestoreTheSharedSource() {
    val showDetail = mutableStateOf(false)
    lateinit var shared: LayerBackdrop
    compose.setContent {
      shared = rememberGlassBackdrop().layer()
      Box {
        Box(Modifier.size(80.dp).layerBackdrop(shared, frozen = { true }).background(Color.Red))
        if (showDetail.value) {
          Box(Modifier.size(60.dp).layerBackdrop(shared, frozen = { true }).background(Color.Green))
        }
        Reader(shared)
      }
    }
    frame()
    val original = checkNotNull(shared.pageSourceOrSelf()).graphicsLayer
    val originalWidth = original.size.width
    showDetail.value = true
    frame()
    val detail = checkNotNull(shared.pageSourceOrSelf()).graphicsLayer
    assertTrue(original.isReleased)
    assertTrue(detail.size.width in 1 until originalWidth)
    showDetail.value = false
    frame()
    val restored = checkNotNull(shared.pageSourceOrSelf()).graphicsLayer
    assertTrue(detail.isReleased)
    assertEquals(originalWidth, restored.size.width)
    assertTrue(shared.isReadyToSample())
    assertEquals(3L, records(shared))
  }

  private fun presentation(sheet: Boolean) {
    val visible = mutableStateOf(false)
    lateinit var layer: LayerBackdrop
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        val host = rememberFluidGlassModalHostState()
        layer = page.layer()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(layer, frozen = { true }).background(Color.Red))
            if (sheet) {
              FluidGlassSheet(visible.value, {}, detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large)) {
                Box(Modifier.size(40.dp))
              }
            } else {
              FluidGlassModalPortal(visible = visible.value, onDismissRequest = {}) { Box(Modifier.size(40.dp)) }
            }
            FluidGlassModalHost(host, page)
          }
        }
      }
    }
    modalFrame()
    assertEquals(0L, records(layer))
    visible.value = true
    modalFrame()
    assertEquals(1L, records(layer))
    visible.value = false
    compose.mainClock.advanceTimeBy(2_000)
    modalFrame()
    assertFalse(layer.isReadyToSample())
    assertEquals(1L, records(layer))
  }

  @Test fun sharedBarSwitchesSourceOnPushAndPop() {
    val stack = mutableStateOf(listOf("root"))
    val pages = mutableMapOf<String, LayerBackdrop>()
    compose.setContent {
      val root = rememberGlassBackdrop().layer()
      val detail = rememberGlassBackdrop().layer()
      pages["root"] = root
      pages["detail"] = detail
      Box {
        FluidPageHost(stack = stack.value, onBack = {}) { key ->
          Box(Modifier.fillMaxSize().layerBackdrop(pages.getValue(key), frozen = { true }).background(Color.Red))
        }
        Reader(pages.getValue(stack.value.last()))
      }
    }
    frame()
    assertEquals(1L, records(pages.getValue("root")))
    assertEquals(0L, records(pages.getValue("detail")))
    stack.value = listOf("root", "detail")
    frame()
    assertEquals(1L, records(pages.getValue("detail")))
    assertEquals(1L, records(pages.getValue("root")))
    stack.value = listOf("root")
    frame()
    assertEquals(2L, records(pages.getValue("root")))
    assertEquals(1L, records(pages.getValue("detail")))
  }
}
