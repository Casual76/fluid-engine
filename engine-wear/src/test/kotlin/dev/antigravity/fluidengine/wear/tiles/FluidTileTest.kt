package dev.antigravity.fluidengine.wear.tiles

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.renderer.TileRenderer
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.wear.sync.FluidWearStatus
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.25 · La Tile Fluid (`tile-e-complicazioni`): il layout di ProtoLayout (il disco di vetro col valore, l'anello sul
 * bordo fino al suo avanzamento, il tasto sul bordo, la frase per TalkBack), i colori dal marchio (un accento scuro si
 * alza finché si legge sul nero, un tema chiaro del telefono non porta mai un fondo bianco al polso), e la Tile
 * **disegnata davvero** dal renderer di Wear OS in una bitmap da 225 dp. Le immagini stanno in `build/f8-25-tile/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w225dp-h225dp-round-xhdpi")
class FluidTileTest {

  private val context = RuntimeEnvironment.getApplication()

  /** Un accento scuro (un blu notte: sul nero non si leggerebbe) e uno chiaro, giallo. */
  private val night = AccentPreset("notte", "Notte", Color(0xFF1A2A6C), Color(0xFF3550C0))
  private val sun = AccentPreset("sole", "Sole", Color(0xFFF2B705), Color(0xFFFFD34D))

  private val lesson = FluidTileContent(
    title = "Matematica",
    value = "10:00",
    detail = "Aula 3 · fra 12 min",
    progress = 0.4f,
    action = FluidTileAction("Apri", ComponentName("dev.esempio.orologio", "dev.esempio.orologio.Agenda")),
  )

  private fun device(widthDp: Int) = DeviceParametersBuilders.DeviceParameters.Builder()
    .setScreenWidthDp(widthDp)
    .setScreenHeightDp(widthDp)
    .setScreenDensity(2f)
    // Il sistema la passa sempre; senza, i testi che non si ingrandiscono (numeri, etichette) si dividono per zero.
    .setFontScale(1f)
    .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
    .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
    .setRendererSchemaVersion(VersionBuilders.VersionInfo.Builder().setMajor(1).setMinor(500).build())
    .build()

  private fun tile(content: FluidTileContent, colors: FluidTileColors, widthDp: Int = 225) =
    materialScope(context, device(widthDp), allowDynamicTheme = false, defaultColorScheme = colors.scheme) {
      fluidTile(content, colors)
    }

  @Test
  fun `il layout ha il disco di vetro, l'anello fino al 40 percento e il tasto sul bordo`() {
    val colors = fluidTileColors(night)
    val walk = Walk().also { it.visit(tile(lesson, colors)) }
    assertTrue("il titolo", "Matematica" in walk.texts)
    assertTrue("il valore", "10:00" in walk.texts)
    assertTrue("la riga sotto", "Aula 3 · fra 12 min" in walk.texts)
    assertTrue("il tasto", "Apri" in walk.texts)
    // L'anello: una pista intera e l'avanzamento di 144 gradi, nell'accento.
    assertTrue(walk.arcLines.any { it.first == 360f && it.second == colors.argb.ringTrack })
    assertTrue(walk.arcLines.any { abs(it.first - 144f) < 0.01f && it.second == colors.argb.accent })
    // Il disco: il ripiego del vetro, tondo (raggio = meta' del diametro) col filetto.
    val disc = walk.discs.single { it.color == colors.argb.discFill }
    assertEquals(colors.argb.discFill, disc.color)
    assertEquals(225 * FluidTileDimens.DiscFractionWithDetail / 2f, disc.radius, 0.01f)
    assertEquals(colors.argb.discRim, disc.border)
    // TalkBack legge tutto in una frase.
    assertTrue("Matematica. 10:00. Aula 3 · fra 12 min" in walk.descriptions)
    // Il tasto apre l'attivita' dell'orologio.
    assertTrue(walk.launches.contains("dev.esempio.orologio.Agenda"))
  }

  @Test
  fun `senza avanzamento non c'e' anello, senza azione non c'e' tasto, il disco cresce`() {
    val colors = fluidTileColors()
    val walk = Walk().also { it.visit(tile(FluidTileContent(title = "Posta", value = "3"), colors, widthDp = 192)) }
    assertTrue(walk.arcLines.isEmpty())
    assertFalse("Apri" in walk.texts)
    assertEquals(192 * FluidTileDimens.DiscFraction / 2f, walk.discs.single { it.color == colors.argb.discFill }.radius, 0.01f)
  }

  @Test
  fun `uno stato vecchio si spegne e lo dice`() {
    val status = FluidWearStatus("lezione", "Matematica", "10:00", detail = "Aula 3", updatedAtMillis = 0, staleAfterMillis = 100)
    val content = FluidTileContent.of(status, nowMillis = 200)
    assertTrue(content.stale)
    val colors = fluidTileColors()
    val walk = Walk().also { it.visit(tile(content, colors)) }
    assertTrue(FluidTileContent.StaleDetail in walk.texts)
    assertFalse("Aula 3" in walk.texts)
    assertEquals(colors.argb.stale, walk.textColors["10:00"])
    val fresh = Walk().also { it.visit(tile(content.copy(stale = false), colors)) }
    assertEquals(colors.argb.onDisc, fresh.textColors["10:00"])
  }

  @Test
  fun `i colori stanno sul nero, leggibili, con un accento scuro e uno chiaro e col telefono in chiaro`() {
    for (brand in listOf(night, sun)) {
      for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)) {
        val colors = fluidTileColors(brand, EngineSettings(themeMode = mode))
        val a = colors.argb
        assertEquals("fondo nero ($brand, $mode)", AndroidColor.BLACK, a.background)
        assertEquals(AndroidColor.BLACK, colors.scheme.background.staticArgb)
        assertTrue("l'accento si legge sul nero: ${contrast(a.accent, a.background)}", contrast(a.accent, a.background) >= 3f)
        assertTrue("il testo sul disco: ${contrast(a.onDisc, a.discFill)}", contrast(a.onDisc, a.discFill) >= 4.5f)
        assertTrue("il testo piccolo sul nero: ${contrast(a.secondary, a.background)}", contrast(a.secondary, a.background) >= 4.5f)
        assertTrue("il testo del tasto: ${contrast(a.onAccent, a.accent)}", contrast(a.onAccent, a.accent) >= 3f)
      }
    }
    // Il blu notte e' stato alzato: non e' piu' quello del marchio.
    assertTrue(Color(fluidTileColors(night).argb.accent).luminance() >= 0.22f - 0.01f)
  }

  @Test
  fun `il renderer di Wear OS disegna la Tile con anello in alto, disco al centro, tasto in basso`() {
    for ((name, brand) in listOf("notte" to night, "sole" to sun)) {
      val colors = fluidTileColors(brand)
      val bitmap = render(tile(lesson, colors), sizePx = 450)
      save(bitmap, "tile-lezione-$name.png")
      // L'anello a ore dodici (l'avanzamento parte da li') nell'accento, la pista a ore nove scura.
      assertNear("anello a ore dodici", colors.argb.accent, bitmap.getPixel(225, 3))
      assertTrue("a ore nove c'e' solo la pista", Color(bitmap.getPixel(3, 225)).luminance() < 0.05f)
      // Il disco: abbastanza pixel del ripiego del vetro (un cerchio di 81 dp meno il testo).
      val discPixels = count(bitmap) { near(colors.argb.discFill, it, 6) }
      assertTrue("pixel del disco: $discPixels", discPixels > 8_000)
      // Il valore e' scritto davvero nel disco, in bianco (un testo a misura infinita non si vede: e' successo).
      val valuePixels = count(bitmap, top = 100, bottom = 300) { near(colors.argb.onDisc, it, 30) }
      assertTrue("pixel del valore: $valuePixels", valuePixels > 300)
      // Il tasto sul bordo in basso, nell'accento, con la sua etichetta.
      val buttonPixels = count(bitmap, top = 340) { near(colors.argb.accent, it, 10) }
      assertTrue("pixel del tasto: $buttonPixels", buttonPixels > 2_000)
      val labelPixels = count(bitmap, top = 340) { near(colors.argb.onAccent, it, 40) }
      assertTrue("pixel dell'etichetta: $labelPixels", labelPixels > 60)
    }
    save(render(tile(FluidTileContent(title = "PampAI", value = "Chiedi", action = FluidTileAction("Parla")), fluidTileColors()), 450), "tile-chiedi.png")
    save(render(tile(FluidTileContent(title = "Posta", value = "3", detail = "non lette"), fluidTileColors(), widthDp = 192), 384, widthDp = 192), "tile-posta-192.png")
  }

  // --- Il giro del layout ---

  private class Disc(val color: Int, val radius: Float, val border: Int?)

  private class Walk {
    val texts = ArrayList<String>()
    val textColors = HashMap<String, Int>()
    val arcLines = ArrayList<Pair<Float, Int>>()
    val discs = ArrayList<Disc>()
    val descriptions = ArrayList<String>()
    val launches = ArrayList<String>()

    fun visit(element: LayoutElementBuilders.LayoutElement) {
      when (element) {
        is LayoutElementBuilders.Box -> {
          modifiers(element.modifiers)
          val background = element.modifiers?.background
          val corner = background?.corner?.radius?.value
          val width = (element.width as? androidx.wear.protolayout.DimensionBuilders.DpProp)?.value
          if (background != null && corner != null && width != null && abs(corner - width / 2f) < 0.01f) {
            discs += Disc(background.color!!.argb, corner, element.modifiers?.border?.color?.argb)
          }
          element.contents.forEach(::visit)
        }
        is LayoutElementBuilders.Column -> { modifiers(element.modifiers); element.contents.forEach(::visit) }
        is LayoutElementBuilders.Row -> { modifiers(element.modifiers); element.contents.forEach(::visit) }
        is LayoutElementBuilders.Text -> {
          val value = element.text?.value ?: return
          texts += value
          element.fontStyle?.color?.argb?.let { textColors[value] = it }
        }
        is LayoutElementBuilders.Arc -> element.contents.forEach { c ->
          when (c) {
            is LayoutElementBuilders.ArcLine -> arcLines += (c.length?.value ?: 0f) to (c.color?.argb ?: 0)
            is LayoutElementBuilders.ArcAdapter -> c.content?.let(::visit)
            else -> Unit
          }
        }
        else -> Unit
      }
    }

    private fun modifiers(m: ModifiersBuilders.Modifiers?) {
      m?.semantics?.contentDescription?.value?.let { descriptions += it }
      val action = m?.clickable?.onClick
      if (action is androidx.wear.protolayout.ActionBuilders.LaunchAction) {
        action.androidActivity?.className?.let { launches += it }
      }
    }
  }

  // --- Il disegno ---

  private fun render(element: LayoutElementBuilders.LayoutElement, sizePx: Int, widthDp: Int = 225): Bitmap {
    val parent = FrameLayout(context)
    val renderer = TileRenderer(context, { it.run() }, { })
    val future = renderer.inflateAsync(
      LayoutElementBuilders.Layout.fromLayoutElement(element),
      ResourceBuilders.Resources.Builder().setVersion("1").build(),
      parent,
    )
    val deadline = System.currentTimeMillis() + 10_000
    while (!future.isDone && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(5)
    }
    assertNotNull(future.get(1, TimeUnit.SECONDS))
    val spec = View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
    parent.measure(spec, spec)
    parent.layout(0, 0, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.BLACK)
    parent.draw(canvas)
    return bitmap
  }

  private fun save(bitmap: Bitmap, name: String) {
    val dir = File("build/f8-25-tile").apply { mkdirs() }
    File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun count(bitmap: Bitmap, top: Int = 0, bottom: Int = bitmap.height, test: (Int) -> Boolean): Int {
    var n = 0
    for (y in top until bottom) for (x in 0 until bitmap.width) if (test(bitmap.getPixel(x, y))) n++
    return n
  }

  private fun near(expected: Int, actual: Int, tolerance: Int): Boolean =
    abs(AndroidColor.red(expected) - AndroidColor.red(actual)) <= tolerance &&
      abs(AndroidColor.green(expected) - AndroidColor.green(actual)) <= tolerance &&
      abs(AndroidColor.blue(expected) - AndroidColor.blue(actual)) <= tolerance

  private fun assertNear(message: String, expected: Int, actual: Int) =
    assertTrue("$message: atteso ${Integer.toHexString(expected)}, trovato ${Integer.toHexString(actual)}", near(expected, actual, 24))

  private fun contrast(a: Int, b: Int): Float {
    val la = Color(a).luminance() + 0.05f
    val lb = Color(b).luminance() + 0.05f
    return max(la, lb) / min(la, lb)
  }
}
