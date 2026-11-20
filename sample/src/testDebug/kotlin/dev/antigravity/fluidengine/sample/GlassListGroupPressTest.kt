@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidListContextPress
import dev.antigravity.fluidengine.ui.fluid.FluidContextMenuController
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.contextMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.sample.material.MaterialTarget
import dev.antigravity.fluidengine.sample.material.trackBounds
import dev.antigravity.fluidengine.sample.measure.rememberScenarioFinger
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.clipListCorners
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h640dp-mdpi")
class GlassListGroupPressTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun stessiPixelAncheDoveIlBaglioreToccaGliAngoli() {
    val optimized = mutableStateOf(false)
    val shape = ContinuousCornerShape(36.dp)
    compose.setContent {
      Box(Modifier.fillMaxSize().background(Color(0xff214367)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(352.dp, 256.dp)
          .then(if (optimized.value) Modifier.clipListCorners(shape, 36.dp) else Modifier.clip(shape))
          .drawBehind {
            drawRect(Color(0xff994617))
            drawRect(Color.White.copy(alpha = 0.27f), blendMode = BlendMode.Plus)
            for (y in 0 until size.height.toInt() step 9) {
              drawRect(Color.Black.copy(alpha = 0.4f), Offset(0f, y.toFloat()), Size(size.width, 2f))
            }
          })
      }
    }
    val before = capture("contorno-nativo")
    assertEquals(Color(0xff214367).toArgb(), before.getPixel(4, 4))
    compose.runOnIdle { optimized.value = true }
    val after = capture("contorno-gruppo")
    assertEquals(Color(0xff214367).toArgb(), after.getPixel(4, 4))
    assertTrue("il confronto deve contenere anche il bordo antialias", colors(before).toSet().size > 20)
    assertTrue("pixel del gruppo, compresi gli angoli", before.sameAs(after))
  }

  @Test fun listaChiara() = listFrames(ThemeMode.LIGHT, "chiaro")
  @Test fun listaScura() = listFrames(ThemeMode.DARK, "scuro")

  private fun listFrames(mode: ThemeMode, theme: String) {
    val first = MaterialTarget()
    val last = MaterialTarget()
    val background = if (mode == ThemeMode.DARK) Color(0xff192734) else Color(0xffe0eaf4)
    lateinit var scroll: ScrollState
    lateinit var finger: MaterialFinger
    var hardware = false
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = mode, dynamicColorEnabled = false) }, systemBars = false) {
        finger = rememberScenarioFinger()
        scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().drawBehind {
          if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) hardware = true
          drawRect(background)
        }.verticalScroll(scroll).padding(horizontal = 24.dp)) {
          Spacer(Modifier.height(92.dp))
          FluidListGroup {
            repeat(4) { i ->
              if (i > 0) FluidListDivider()
              val marker = when (i) {
                0 -> Modifier.trackBounds(first)
                3 -> Modifier.trackBounds(last)
                else -> Modifier
              }
              FluidListRow("Nota ${i + 1}", "Una riga della lista", modifier = marker, onClick = {},
                contextActions = if (i == 0 || i == 3) ({ emptyList() }) else null)
            }
          }
          Spacer(Modifier.height(950.dp))
        }
      }
    }
    fun frames(n: Int) {
      repeat(n) {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        Snapshot.sendApplyNotifications()
      }
      compose.waitForIdle()
    }
    fun pose(name: String): Bitmap {
      val bitmap = capture("$theme-$name")
      assertEquals("fondale della lista $theme", background.toArgb(), bitmap.getPixel(4, 4))
      assertTrue("la lista deve contenere testo e angoli", colors(bitmap).toSet().size > 32)
      return bitmap
    }
    frames(8)
    val rest = pose("riposo")
    assertTrue("la lista deve essere disegnata su canvas hardware", hardware)
    val top = checkNotNull(first.bounds).topLeft + Offset(18f, 14f)
    compose.runOnUiThread { finger.down(top) }
    frames(10)
    assertTrue("la pressione deve essere visibile", !rest.sameAs(pose("pressione-alta-160")))
    frames(9)
    pose("pressione-alta-304")
    compose.runOnUiThread { finger.up(top) }
    frames(50)
    val start = checkNotNull(first.bounds).center
    compose.runOnUiThread { finger.down(start) }
    frames(2)
    compose.runOnUiThread { finger.move(start - Offset(0f, 24f)) }
    frames(1)
    compose.runOnUiThread { finger.move(start - Offset(0f, 48f)) }
    frames(1)
    assertTrue("lo scroll deve avere spostato il gruppo", scroll.value > 0)
    pose("scroll")
    compose.runOnUiThread { finger.cancel(start - Offset(0f, 48f)) }
    frames(50)
    val scrolled = pose("scorsa-ferma")
    val bottom = checkNotNull(last.bounds).bottomRight - Offset(18f, 14f)
    compose.runOnUiThread { finger.down(bottom) }
    frames(19)
    assertTrue("la riga bassa deve rispondere", !scrolled.sameAs(pose("scorsa-pressione-bassa")))
    compose.runOnUiThread { finger.up(bottom) }
  }

  @Test fun laCardConservaLaRigaAlDistaccoESiAggiornaAllaSecondaApertura() {
    val color = mutableStateOf(Color.Red)
    val width = mutableStateOf(60.dp)
    lateinit var controller: FluidContextMenuController
    compose.setContent {
      CompositionLocalProvider(LocalFluidGlassModalHostState provides rememberFluidGlassModalHostState()) {
        controller = rememberFluidContextMenu(actions = { listOf(FluidContextAction("Apri") {}) })
        Column {
          Box(Modifier.size(width.value, 40.dp).contextMenuAnchor(controller, listRow = true, group = null)
            .drawBehind { drawRect(color.value) })
          Box(Modifier.size(120.dp, 40.dp).drawBehind {
            if (controller.entry.lifted) drawLayer(controller.layer)
          })
        }
      }
    }
    fun area(c: Color) = colors(windowBitmap()).count { it == c.toArgb() }
    compose.runOnIdle { controller.arm(true) }
    assertEquals(60 * 40, area(Color.Red))
    compose.runOnIdle { width.value = 88.dp; color.value = Color.Blue }
    assertEquals(88 * 40, area(Color.Blue))
    compose.runOnIdle { assertTrue(controller.open()) }
    assertEquals("prima posa, dimensione e tinta attuali", 88 * 40, area(Color.Blue))
    compose.runOnIdle { color.value = Color.Green }
    assertEquals("sorgente celata, registrazione congelata", 88 * 40, area(Color.Blue))
    compose.runOnIdle {
      controller.dismiss()
      controller.entry.lifted = false
      controller.arm(false)
    }
    assertEquals(88 * 40, area(Color.Green))
    compose.runOnIdle { controller.arm(true); assertTrue(controller.open()) }
    assertEquals("seconda apertura", 88 * 40, area(Color.Green))
  }

  @Test fun ilGruppoConservaGliAngoliFinoAllaRiconsegna() {
    val group = FluidListContextPress()
    val nativeClip = mutableStateOf(false)
    val shape = ContinuousCornerShape(24.dp)
    lateinit var controller: FluidContextMenuController
    compose.setContent {
      CompositionLocalProvider(LocalFluidGlassModalHostState provides rememberFluidGlassModalHostState()) {
        controller = rememberFluidContextMenu(actions = { listOf(FluidContextAction("Apri") {}) })
        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
          val clip = if (group.active && !nativeClip.value) Modifier.clipListCorners(shape, 24.dp) else Modifier.clip(shape)
          Column(Modifier.size(320.dp, 160.dp).then(clip).background(Color.White)) {
            Box(Modifier.size(320.dp, 80.dp).contextMenuAnchor(controller, listRow = true, group = group)
              .background(Color.Red))
            Box(Modifier.size(320.dp, 80.dp).background(Color.Blue))
          }
        }
      }
    }
    val rest = windowBitmap()
    compose.runOnIdle {
      assertTrue(!group.active)
      group.press(controller)
      assertTrue(controller.open())
    }
    compose.runOnIdle { group.release(controller) }
    val lifted = windowBitmap()
    compose.runOnIdle {
      assertTrue("il dito è alzato e la sorgente è ancora sollevata", group.active)
      assertTrue(!rest.sameAs(lifted))
      nativeClip.value = true
    }
    assertTrue("angoli della lista sotto la card", lifted.sameAs(windowBitmap()))
    compose.runOnIdle {
      nativeClip.value = false
      controller.dismiss()
    }
    compose.runOnIdle {
      assertTrue("il ritorno è ancora in corso", group.active)
      controller.entry.lifted = false
    }
    compose.waitForIdle()
    compose.runOnIdle { assertTrue("ritaglio ordinario alla riconsegna", !group.active) }
    assertTrue("la riga e gli angoli tornano identici", rest.sameAs(windowBitmap()))
  }

  private fun colors(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  }

  private fun capture(name: String): Bitmap {
    val bitmap = windowBitmap()
    System.getenv("F73C2_HWUI_FRAMES")?.let { dir ->
      File(dir, "$name.png").also { it.parentFile.mkdirs() }.outputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
      }
    }
    System.getenv("F73C2_HWUI_REFERENCE")?.let { dir ->
      val reference = requireNotNull(BitmapFactory.decodeFile(File(dir, "$name.png").absolutePath))
      assertEquals("$name: larghezza", reference.width, bitmap.width)
      assertEquals("$name: altezza", reference.height, bitmap.height)
      assertTrue("$name: differenza da 626906a", colors(bitmap).contentEquals(colors(reference)))
    }
    return bitmap
  }

  // Legge la finestra posseduta dal ViewRoot con PixelCopy e attende il callback. Il vecchio
  // decor.draw() costruiva un secondo RenderNode scollegato dalla finestra osservata.
  private fun windowBitmap(): Bitmap {
    compose.waitForIdle()
    val window = compose.activity.window
    val content = window.findViewById<View>(android.R.id.content)
    val location = IntArray(2)
    content.getLocationInWindow(location)
    val bounds = android.graphics.Rect(location[0], location[1], location[0] + content.width, location[1] + content.height)
    val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread {
      PixelCopy.request(window, bounds, bitmap, { result = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("acquisizione della finestra", PixelCopy.SUCCESS, result)
    return bitmap
  }
}
