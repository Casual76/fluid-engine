package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceKey
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.SceneAsk
import dev.antigravity.fluidengine.ui.glass.field.trace.completeRestBody
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeMs
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidFromGlass
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I preset di «un elemento che diventa pagina» (F6B.5c): il tipo, le sorgenti che l'host ne ricava e, con
 * `GLASS_F6B5C_DIR`, le tracce del banco a 120 Hz (`elemento-dalla-barra`, `elemento-giu-chiude`,
 * `griglia-visore-griglia`) scritte dalla macchina vera delle pagine con il suo orologio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidZoomPresetsTest {

  @Test fun iPresetSiConfrontanoPerValoreESonoAllargabili() {
    assertEquals(FluidZoomStyle.Grow, FluidZoomStyle.grow())
    assertEquals(FluidZoomStyle.Grow.hashCode(), FluidZoomStyle.grow().hashCode())
    assertNotEquals(FluidZoomStyle.Grow, FluidZoomStyle.grow(optics = GlassDefaults.optics(GlassRole.Floating)))
    assertEquals(FluidZoomStyle.fromToolbar("scrivi"), FluidZoomStyle.fromToolbar("scrivi"))
    assertNotEquals(FluidZoomStyle.FromToolbar, FluidZoomStyle.fromToolbar("scrivi"))
    assertNotEquals(FluidZoomStyle.Zoom, FluidZoomStyle.Rise)
    val all = listOf(FluidZoomStyle.Zoom, FluidZoomStyle.Rise, FluidZoomStyle.Grow, FluidZoomStyle.FromToolbar)
    assertEquals(4, all.toSet().size)
    for (style in all) assertEquals(FluidPageDismiss.Down, style.dismiss)
    assertEquals(listOf(false, true, true, false), all.map { it.opensByDragging })
    assertEquals(
      listOf("FluidZoomStyle.Zoom", "FluidZoomStyle.Rise", "FluidZoomStyle.Grow", "FluidZoomStyle.FromToolbar"),
      all.map { it.toString() },
    )
    assertEquals("FluidZoomStyle.fromToolbar(scrivi)", FluidZoomStyle.fromToolbar("scrivi").toString())
    // Un `when` su un preset ha sempre un ramo `else`: un preset nuovo domani non rompe le app.
    fun name(style: FluidZoomStyle) = when (style) {
      FluidZoomStyle.Zoom -> "zoom"
      FluidZoomStyle.Rise -> "sale"
      else -> "altro"
    }
    assertEquals(listOf("zoom", "sale", "altro", "altro"), all.map(::name))
  }

  @Test fun lHostCompletaLaSorgenteSecondoIlPreset() {
    val sources = FluidZoomSources()
    val ns = FluidGlassNamespace()
    val declared = FluidTransitionSource("card", ns)
    assertSame(declared, sources.sourceOf("pagina", null, declared) { null })
    assertSame(declared, sources.sourceOf("pagina", FluidZoomStyle.Zoom, declared) { null })
    assertSame(declared, sources.sourceOf("pagina", FluidZoomStyle.Rise, declared) { null })
    assertNull(sources.sourceOf("pagina", FluidZoomStyle.Rise, null) { null })
    val tint = GlassTint(Color(0x33FF8800), Color(0xFFFF8800), Color(0x22FFFFFF))
    val grown = requireNotNull(sources.sourceOf("pagina", FluidZoomStyle.grow(tint), declared) { null })
    assertEquals(GrowingPageGlass(tint, GlassDefaults.buttonOptics()), grown.growingGlass)
    // La sorgente decorata è la stessa identità: consumarla nasconde la vista marcata con quella di partenza.
    grown.consume()
    assertTrue(declared.isHidden)
    grown.release()
    assertFalse(declared.isHidden)
  }

  @Test fun ilTastoDellaBarraSiTrovaNelPostoDellaPaginaSotto() {
    val sources = FluidZoomSources()
    val inbox = Any()
    sources.track(listOf("posta", "scrivi")) { false }
    assertEquals("posta", sources.below("scrivi"))
    val style = FluidZoomStyle.fromToolbar("scrivi")
    val owners = mapOf<Any, Any>("posta" to inbox)
    val source = requireNotNull(sources.sourceOf("scrivi", style, null, owners::get))
    assertNotNull(source.growingGlass)
    // Il tasto vero è `rememberFluidToolbarTransitionSource("scrivi")` nella pagina «posta»: l'host ne legge la geometria
    // ma riceve una sorgente di passaggio, e consumarla non toglie la faccia al tasto (la barra la cambia da sé).
    val sameAsBar = FluidTransitionSource(FluidTransitionSourceKey(inbox, "scrivi"))
    source.consume()
    assertTrue(source.isConsumed)
    assertFalse("la barra tiene la faccia del tasto", sameAsBar.isHidden)
    source.release()
    // Senza la pagina sotto non c'è sorgente: vale il ripiego.
    assertNull(sources.sourceOf("scrivi", style, null) { null })
    // Con una sorgente data dalla rotta (`FromToolbar`), lo stesso: la barra non perde niente.
    val declared = FluidTransitionSource("altro", FluidGlassNamespace())
    val passage = requireNotNull(sources.sourceOf("scrivi", FluidZoomStyle.FromToolbar, declared, owners::get))
    passage.consume()
    assertFalse(declared.isHidden)
    passage.release()
    // Mentre la pagina esce resta ricordata; a riposo, fuori dalla pila, si dimentica.
    sources.track(listOf("posta")) { it == "scrivi" }
    assertEquals("posta", sources.below("scrivi"))
    sources.track(listOf("posta")) { false }
    assertNull(sources.below("scrivi"))
  }

  @Test fun unTastoNonAncoraAlSuoPostoTornaDoveLaPaginaLAvevaTrovato() {
    val sources = FluidZoomSources()
    sources.track(listOf("posta", "scrivi")) { false }
    val source = requireNotNull(sources.sourceOf("scrivi", FluidZoomStyle.fromToolbar("x"), null) { Any() })
    // Nessuna vista posata: nessuna geometria, e niente da ricordare.
    assertNull(sources.resolve(source))
    // Una sorgente qualsiasi senza preset passa com'era.
    assertNull(sources.resolve(FluidTransitionSource("y", FluidGlassNamespace())))
  }

  // --- Le tracce del banco ---------------------------------------------------------------------------------------------

  private val full = FluidZoomShape(Rect(0f, 0f, 320f, 480f))

  private inner class Run(
    val sourceBox: Rect,
    val radius: Float,
    val style: FluidZoomStyle,
    val toolbar: Boolean = false,
  ) : AutoCloseable {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob() + clock)
    val sources = FluidZoomSources()
    val ns = FluidGlassNamespace()
    val owner = Any()
    val declared = if (toolbar) null else FluidTransitionSource("sorgente", ns)
    var stack = listOf("lista")
    val state = FluidPageHostState().also { s ->
      s.scope = scope
      s.width = full.bounds.width
      s.height = full.bounds.height
      s.transitionOf = { page -> if (page == "pagina") FluidPageTransition.Expand else FluidPageTransition.Slide }
      s.dismissOf = { page -> if (page == "pagina") style.dismiss else FluidPageDismiss.None }
      s.sourceOf = { page -> sources.sourceOf(page, style.takeIf { page == "pagina" }, declared) { owner } }
      s.resolveSource = { FluidTransitionSourceGeometry(sourceBox, RoundedCornerShape(radius.dp)) }
      s.onBack = { onStack(listOf("lista")) }
    }
    var time = 0L

    init {
      onStack(stack)
    }
    val frames = mutableListOf<Sample>()

    fun onStack(next: List<String>) {
      stack = next
      sources.track(next) { it == state.upper || it == state.lower }
      state.onStack(next)
    }

    /** La sorgente adesso è nascosta (consumata): la sua vista non si disegna. */
    fun sourceHidden(): Boolean = (declared ?: FluidTransitionSource(FluidTransitionSourceKey(owner, "scrivi"))).isHidden

    fun sample() {
      val p = state.fractionToward { it == "pagina" }
      val shape = state.zoomShape() ?: if (p >= 1f) full else FluidZoomShape(sourceBox, FluidPageCorners(radius, radius, radius, radius))
      frames += Sample(p, shape, if (!state.isMoving && p == 1f) 1f else state.solidOpacity, sourceHidden(), state.isMoving)
    }

    fun tick() {
      clock.sendFrame(time)
      time += 8_333_333L
      sample()
    }

    fun push() {
      onStack(listOf("lista", "pagina"))
      state.pagePlaced("pagina")
    }

    fun pop() = onStack(listOf("lista"))

    /** Il dito che tira giù la pagina aperta: [distance] px in [steps] fotogrammi, con la velocità dell'ultimo passo. */
    fun drag(token: Int, distance: Float, steps: Int): Float {
      var previous = 0f
      var speed = 0f
      for (i in 1..steps) {
        val part = ((1 - cos(Math.PI * i / steps)) / 2).toFloat()
        val next = distance * part
        speed = (next - previous) * 120
        state.moveVertical(token, next - previous, speed)
        tick()
        previous = next
      }
      return speed
    }

    fun settle() {
      var i = 0
      while (state.isMoving && i++ < 240) tick()
      assertFalse(state.isMoving)
    }

    fun hold(count: Int) = repeat(count) { tick() }

    override fun close() {
      scope.cancel()
      state.releaseSources()
    }
  }

  private data class Sample(val p: Float, val shape: FluidZoomShape, val solid: Float, val sourceHidden: Boolean, val moving: Boolean)

  @Test fun tracceDelleScenePresetA120Hz() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B5C_DIR"))

    // «Scrivi» dalla barra: il tasto in basso a destra diventa il compositore al tocco e ci torna col tasto indietro.
    Run(ComposeButton, 24f, FluidZoomStyle.fromToolbar("scrivi"), toolbar = true).use { r ->
      r.sample()
      r.hold(4)
      r.push()
      r.settle()
      assertEquals("pagina", r.state.upper)
      r.hold(16)
      r.pop()
      r.settle()
      assertEquals("lista", r.state.upper)
      r.hold(4)
      assertTrue("la barra non perde mai la faccia del tasto", r.frames.none { it.sourceHidden })
      checkJourney(r.frames, grow = true)
      if (output != null) exportToolbar(output, r.frames)
    }

    // «In riproduzione» tirata giù col dito rientra nel mini lettore.
    Run(MiniRow, 24f, FluidZoomStyle.Rise).use { r ->
      r.push()
      r.settle()
      r.frames.clear()
      r.sample()
      r.hold(4)
      val token = requireNotNull(r.state.beginVertical(null))
      r.drag(token, 120f, 22)
      r.drag(token, -40f, 10)
      val speed = r.drag(token, 150f, 18)
      r.state.releaseVertical(token, maxOf(speed, 900f))
      r.settle()
      assertEquals("lista", r.state.upper)
      r.hold(4)
      checkJourney(r.frames, grow = false, startsOpen = true)
      if (output != null) exportDown(output, r.frames)
    }

    // Il mini lettore tirato su fino a «In riproduzione», lasciato, poi tirato giù fino a rientrare.
    Run(MiniRow, 24f, FluidZoomStyle.Rise).use { r ->
      r.sample()
      r.hold(4)
      val open = requireNotNull(r.state.beginVertical { r.push() })
      val up = r.drag(open, -400f, 48)
      r.state.releaseVertical(open, minOf(up, -900f))
      r.settle()
      assertEquals("pagina", r.state.upper)
      r.hold(16)
      val close = requireNotNull(r.state.beginVertical(null))
      val down = r.drag(close, 260f, 36)
      r.state.releaseVertical(close, maxOf(down, 900f))
      r.settle()
      assertEquals("lista", r.state.upper)
      r.hold(4)
      checkJourney(r.frames, grow = false)
      if (output != null) exportDown(output, r.frames, "mini-lettore-sale-e-torna")
    }

    // La griglia: una miniatura si apre nel visore al tocco e si richiude trascinando in giù.
    Run(Thumbnail, 10f, FluidZoomStyle.Zoom).use { r ->
      r.sample()
      r.hold(4)
      r.push()
      r.settle()
      r.hold(12)
      val token = requireNotNull(r.state.beginVertical(null))
      val speed = r.drag(token, 170f, 26)
      r.state.releaseVertical(token, maxOf(speed, 900f))
      r.settle()
      assertEquals("lista", r.state.upper)
      r.hold(4)
      checkJourney(r.frames, grow = false)
      if (output != null) exportGrid(output, r.frames)
    }
  }

  /** Il viaggio d'oro: nasce dalla sorgente, ci torna, nessun salto della sagoma fra due fotogrammi in movimento. */
  private fun checkJourney(frames: List<Sample>, grow: Boolean, startsOpen: Boolean = false) {
    assertEquals(if (startsOpen) 1f else 0f, frames.first().p, 0f)
    assertEquals(0f, frames.last().p, 0f)
    assertTrue(frames.any { it.p == 1f })
    for ((a, b) in frames.zipWithNext()) {
      if (!a.moving && !b.moving) continue
      val jump = maxOf(abs(a.shape.bounds.left - b.shape.bounds.left), abs(a.shape.bounds.top - b.shape.bounds.top),
        abs(a.shape.bounds.right - b.shape.bounds.right), abs(a.shape.bounds.bottom - b.shape.bounds.bottom))
      assertTrue("la sagoma salta di $jump px fra p=${a.p} e p=${b.p}", jump < 60f)
    }
    // All'andata la pagina è vetro finché non arriva; al ritorno il vetro torna nei primi passi (F6B.5b, GrowMeltStart).
    if (grow) {
      val opening = frames.take(frames.indexOfFirst { it.p == 1f })
      assertTrue("opaca solo all'arrivo", opening.all { it.solid == 0f })
      assertTrue("di nuovo vetro prima della sorgente", frames.filter { it.moving && it.p < 0.5f }.all { it.solid == 0f })
    }
  }

  // --- La scrittura delle tracce -----------------------------------------------------------------------------------

  private val paper = "#F3F3F8"
  private val ink = "#182A3E"

  private fun rect(name: String, box: Rect, radius: Float) = mapOf<String, Any?>(
    "name" to name, "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height, "radius" to radius)

  private fun label(text: String, x: Float, y: Float, size: Int, color: String = ink) =
    mapOf("kind" to "text", "text" to text, "x" to x, "y" to y, "sizeDp" to size, "color" to color)

  private fun face(name: String, box: Rect, alpha: Float, blur: Float, y: Float, items: List<Map<String, Any?>>) =
    mapOf("name" to name, "x" to 0, "y" to y, "w" to box.width, "h" to box.height,
      "alpha" to alpha, "blurSigmaDp" to glassContentSigmaPx(blur, GlassContentDefaults.BlurReference.value), "items" to items)

  private fun icon(name: String, cx: Float, cy: Float, size: Int, color: String = ink) =
    mapOf("kind" to "icon", "icon" to name, "x" to cx, "y" to cy, "sizeDp" to size, "color" to color)

  /** Un contenuto senza fondo posato su [box]: la faccia di una sorgente a riposo. */
  private fun overlay(name: String, box: Rect, items: List<Map<String, Any?>>) = rect(name, box, 0f) + mapOf(
    "kind" to "shape", "color" to "#00000000",
    "content" to listOf(mapOf("name" to name + "-contenuto", "w" to box.width, "h" to box.height, "items" to items)))

  private fun glass(name: String, box: Rect, radius: Float) =
    liquidFromGlass(rect(name, box, radius) + ("role" to "button"), "light", SceneAsk()).also(::completeRestBody)

  /**
   * La lastra di «Cresce»: l'host la disegna con angoli circolari (`AbsoluteRoundedCornerShape` sulla sagoma dello zoom),
   * quindi qui non è una forma continua. Così vale a tutte le densità anche mentre passa da tasto piccolo a pagina.
   */
  private fun slab(name: String, box: Rect, radius: Float): Map<String, Any?> {
    val recipe = glassRecipe(GlassRole.Interactive, box.width, box.height, darkSide = false, optics = GlassDefaults.buttonOptics())
    val note = "glassRecipe: ruolo button, %.1f x %.1f dp, lato chiaro, angoli circolari".format(box.width, box.height)
    return liquidElement(rect(name, box, radius), recipe, null, note, continuous = false).also(::completeRestBody)
  }

  /** La pagina d'arrivo nella sua sagoma, con le due facce: quella della sorgente e i comandi che arrivano per ultimi. */
  private fun pageElement(
    s: Sample,
    grow: Boolean,
    source: Rect,
    origin: List<Map<String, Any?>>,
    commands: List<Map<String, Any?>>,
  ): Map<String, Any?> {
    val p = s.p
    val b = s.shape.bounds
    val f = FluidZoom.faces(p)
    val solid = if (grow) s.solid else if (p == 0f) 0f else 1f
    val base = rect("pagina", b, s.shape.corners.topLeft) + mapOf(
      "kind" to "shape", "corner" to "round", "color" to paper + "%02X".format((solid * 255).roundToInt()),
      "_p" to p, "_solid" to s.solid,
      "_sigmaSfondo" to if (grow) FluidPageMotion.GrowBlurDp * FluidZoom.atmosphere(p) else 0f,
      "_veloSfondo" to if (grow) FluidPageMotion.GrowDim * FluidZoom.atmosphere(p) else 0f)
    val sourceScale = b.width / source.width
    val originFace = face("origine", b, f.sourceAlpha, f.sourceBlur, 0f,
      origin.map { item -> scaled(item, sourceScale) })
    val placement = FluidZoom.content(s.shape, full.bounds)
    val shift = full.bounds.topLeft + placement.translation - b.topLeft
    val rise = f.riseDp * if (grow) 1f else placement.scale
    val chrome = face("comandi", full.bounds, f.pageAlpha, f.pageBlur * placement.scale, shift.y + rise, commands) +
      mapOf("x" to shift.x, "scale" to placement.scale)
    return base + ("content" to listOf(originFace, chrome))
  }

  private fun scaled(item: Map<String, Any?>, k: Float): Map<String, Any?> = item.mapValues { (key, value) ->
    when (key) {
      "x", "y", "w", "h", "r" -> (value as Number).toFloat() * k
      "sizeDp" -> ((value as Number).toFloat() * k).roundToInt().coerceAtLeast(1)
      else -> value
    }
  }

  private fun write(output: File, name: String, frames: List<GlassTraceFrame>, page: List<Map<String, Any?>>, grow: Boolean) {
    val background = if (grow) mapOf("kind" to "gradient4", "tl" to "#B5CFDF", "tr" to "#F6DFC5", "bl" to "#6D9EA9", "br" to "#E5BCA9")
      else mapOf("kind" to "color", "color" to paper)
    val head = mapOf("background" to background, "page" to page,
      "probes" to listOf("_p", "_solid", "_sigmaSfondo", "_veloSfondo").map {
        mapOf("name" to it.drop(1), "kind" to "value", "path" to "pagina." + it) })
    GlassTrace(name, "f6b-5c", 1f, "light", 320f, 480f, frames, 120, head).write(File(output, "$name/trace.json"))
  }

  private fun listPage(title: String, rows: List<String>): List<Map<String, Any?>> =
    listOf(label(title, 24f, 54f, 31)) + rows.mapIndexed { i, row -> label(row, 24f, 125f + 66f * i, 20) } +
      (1..rows.size).map { i -> mapOf("kind" to "rect", "x" to 23, "y" to (128 + 66 * (i - 1)), "w" to 274, "h" to 1, "color" to ink + "28") }

  private fun exportToolbar(output: File, samples: List<Sample>) {
    val frames = samples.mapIndexed { i, s ->
      val elements = mutableListOf<Map<String, Any?>>()
      // La lastra che cresce da sotto il tasto, poi la pagina; sopra a tutto il tasto, che resta della barra con la sua
      // faccia (lo scambio dell'icona con quella della barra d'arrivo è della barra, F4A, e non è in questo modello).
      if (s.p > 0f || s.moving) elements += slab("sagoma", s.shape.bounds, s.shape.corners.topLeft)
      if (s.p > 0f || s.moving) elements += pageElement(s, grow = true, source = ComposeButton,
        origin = emptyList(),
        commands = listOf(label("Nuovo messaggio", 23f, 64f, 28), label("A: Giulia Rossi", 24f, 130f, 18),
          label("Oggetto: Cena di sabato", 24f, 172f, 18), label("Ciao Giulia,", 24f, 230f, 16),
          label("sabato va benissimo.", 24f, 254f, 16)))
      elements += glass("tasto", ComposeButton, 24f)
      if (!s.sourceHidden) elements += overlay("faccia", ComposeButton, listOf(icon("fluid:edit|plus", 24f, 24f, 24)))
      GlassTraceFrame(glassTraceTimeMs(i, 120), elements)
    }
    write(output, "elemento-dalla-barra", frames,
      listPage("Posta in arrivo", listOf("Giulia Rossi", "Banca", "Marco", "Biblioteca", "Sara")), grow = true)
  }

  private fun exportDown(output: File, samples: List<Sample>, name: String = "elemento-giu-chiude") {
    val capsule = Rect(MiniRow.left - 4f, MiniRow.top - 4f, MiniRow.right + 4f, MiniRow.bottom + 4f)
    val frames = samples.mapIndexed { i, s ->
      val elements = mutableListOf<Map<String, Any?>>()
      // L'accessorio è dell'app: il suo vetro resta; la sorgente è il contenuto, nascosto mentre la pagina è in scena.
      elements += glass("accessorio", capsule, capsule.height / 2f)
      if (!s.sourceHidden && s.p == 0f) {
        elements += overlay("mini", MiniRow, listOf(label("Il brano", 54f, 30f, 15)))
        elements += rect("copertina-mini", SmallArt, 6f) + mapOf("kind" to "shape", "color" to Cover)
      }
      if (s.p > 0f) {
        elements += pageElement(s, grow = false, source = MiniRow,
          origin = listOf(label("Il brano", 54f, 30f, 15)),
          commands = listOf(label("In riproduzione", 30f, 376f, 14), label("Il brano", 30f, 400f, 24),
            mapOf("kind" to "rect", "x" to 30, "y" to 424, "w" to 260, "h" to 3, "color" to ink)))
        val art = FluidZoom.sharedBounds(SmallArt, LargeArt, s.p, s.shape, FluidZoomShape(MiniRow), full)
        elements += rect("copertina", art, art.width / 12f) + mapOf("kind" to "shape", "color" to Cover)
      }
      GlassTraceFrame(glassTraceTimeMs(i, 120), elements)
    }
    write(output, name, frames,
      listPage("Libreria", listOf("Ascolti recenti", "Playlist", "Album", "Artisti")), grow = false)
  }

  private fun exportGrid(output: File, samples: List<Sample>) {
    val cells = (0 until 12).map(::cell)
    val page = listOf(label("Foto", 24f, 54f, 31)) + cells.mapIndexed { i, box ->
      mapOf("kind" to "rect", "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height, "color" to photo(i))
    }.filterIndexed { i, _ -> i != SourceCell }
    val frames = samples.mapIndexed { i, s ->
      val elements = mutableListOf<Map<String, Any?>>()
      if (!s.sourceHidden && s.p == 0f) elements += rect("miniatura", Thumbnail, 0f) + mapOf("kind" to "shape", "color" to photo(SourceCell))
      if (s.p > 0f) {
        elements += pageElement(s, grow = false, source = Thumbnail, origin = emptyList(),
          commands = listOf(label("Foto ${SourceCell + 1} di 45", 104f, 40f, 17)))
        val art = FluidZoom.sharedBounds(Thumbnail, Viewed, s.p, s.shape, FluidZoomShape(Thumbnail), full)
        elements += rect("foto", art, 0f) + mapOf("kind" to "shape", "color" to photo(SourceCell))
      }
      GlassTraceFrame(glassTraceTimeMs(i, 120), elements)
    }
    write(output, "griglia-visore-griglia", frames, page, grow = false)
  }

  private fun cell(index: Int): Rect = gridCell(index)

  private fun photo(index: Int): String = listOf("#C9705A", "#5A8FC9", "#7FB069", "#E9A23B", "#8D6E63", "#6B3E75",
    "#16697A", "#E5BCA9", "#3A7BD5", "#B66D9C", "#F2994A", "#2F5D34")[index % 12]

  private companion object {
    val ComposeButton = Rect(256f, 412f, 304f, 460f)
    val MiniRow = Rect(20f, 412f, 300f, 460f)
    val SmallArt = Rect(26f, 418f, 62f, 454f)
    val LargeArt = Rect(24f, 76f, 296f, 348f)
    const val Cover = "#E6A379"
    const val SourceCell = 4
    val Thumbnail: Rect = gridCell(SourceCell)
    val Viewed = Rect(0f, 80f, 320f, 400f)
  }
}

/** La miniatura [index] della griglia finta: tre colonne da 16 dp di margine e 4 dp di spazio, sotto il titolo. */
private fun gridCell(index: Int): Rect {
  val size = (320f - 32f - 8f) / 3f
  val left = 16f + index % 3 * (size + 4f)
  val top = 96f + index / 3 * (size + 4f)
  return Rect(left, top, left + size, top + size)
}
