package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.page.*
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class F6dPageDimTraceTest {
  private val w = 400f
  private val h = 600f
  private val card = Rect(32f, 192f, 176f, 300f)
  private val scenarios = listOf("pagina-arriva", "pagina-col-dito", "pagina-col-dito-annullata", "zoom-scurisce")

  @Test fun treCorseIdentichePerTema() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6D_DIR"))
    for (scene in scenarios) for (dark in listOf(false, true)) {
      val trace = record(scene, dark)
      repeat(2) {
        val again = record(scene, dark)
        assertEquals(trace.frames.map { it.elements }, again.frames.map { it.elements })
      }
      if (dir != null) trace.write(File(dir, "${trace.name}/trace.json"))
    }
  }

  private fun record(scene: String, dark: Boolean): GlassTrace {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    val source = FluidTransitionSource("cella", FluidGlassNamespace())
    val zoom = scene == "zoom-scurisce"
    val finger = scene.contains("dito")
    val cancel = scene.endsWith("annullata")
    val sign = if (dark) -1 else 1
    val state = FluidPageHostState().apply {
      this.scope = scope
      width = w
      height = h
      hostRightInWindow = w
      windowWidth = w
      layoutDirectionSign = sign
      transitionOf = { if (zoom) FluidPageTransition.Expand else FluidPageTransition.Slide }
      sourceOf = { if (zoom) source else null }
      resolveSource = { FluidTransitionSourceGeometry(card, RectangleShape) }
      onBack = { onStack(listOf(0)) }
      onStack(if (finger) listOf(0, 1) else listOf(0))
    }
    val back = object : NavigationEventInput() {
      fun start(e: NavigationEvent) = dispatchOnBackStarted(e)
      fun step(e: NavigationEvent) = dispatchOnBackProgressed(e)
      fun finish(cancel: Boolean) {
        if (cancel) dispatchOnBackCancelled() else dispatchOnBackCompleted()
      }
    }
    val dispatcher = NavigationEventDispatcher().apply {
      addInput(back)
      addHandler(FluidPageBackHandler(state).apply { isBackEnabled = true })
    }
    fun event(distance: Float, frame: Int) = NavigationEvent(
      swipeEdge = if (dark) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT,
      touchX = if (dark) w - distance else distance, touchY = h / 2f,
      progress = .1f, frameTimeMillis = 1000L + frame * 1000L / 120L)
    val recorded = mutableListOf<GlassTraceFrame>()
    try {
      for (k in -5..(if (finger) 159 else 187)) {
        if (!finger) {
          if (k == 0) { state.onStack(listOf(0, 1)); state.pagePlaced(1) }
          if (k == 98) state.onStack(listOf(0))
        } else {
          if (k == 0) back.start(event(0f, 0))
          val fraction = when (k) {
            in 1..30 -> k * .025f
            in 31..40 -> .75f - (k - 30) * .025f
            in 41..62 -> .5f
            in 63..72 -> if (cancel) .5f - (k - 62) * .025f else .5f + (k - 62) * .025f
            in 73..82 -> if (cancel) .25f else .75f
            else -> null
          }
          if (fraction != null) back.step(event(fraction * w, k))
          if (k == 83) back.finish(cancel)
        }
        clock.sendFrame(glassTraceTimeNanos(k + 6, 120))
        val elements = mutableListOf<Map<String, Any?>>()
        val showDetail = state.upper == 1
        if (!showDetail || state.isMoving) {
          val dx = if (showDetail) state.pageOffsetX(PageRole.Lower) else 0f
          elements += mapOf("name" to "lista", "x" to dx)
          if (zoom && !source.isHidden) elements += mapOf("name" to "cella")
          val dim = state.dimOn(0)
          if (dim > 0) elements += mapOf("name" to "velo", "x" to dx, "color" to glassColor(Color.Black.copy(alpha = dim)), "_alpha" to dim)
        }
        if (showDetail) {
          val shape = state.zoomShape()?.bounds
          val box = shape ?: Rect(state.pageOffsetX(PageRole.Upper), 0f, state.pageOffsetX(PageRole.Upper) + w, h)
          val face = if (shape != null) state.zoomFaces() else null
          val scale = state.pageScale(PageRole.Upper)
          elements += mapOf("name" to "pagina", "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height,
            "_phase" to state.phase.name, "_exit" to state.upperExitFraction(),
            "content" to listOf(mapOf("name" to "inchiostro", "scale" to scale, "w" to w, "h" to h,
              "x" to (box.width - w) / 2f, "y" to (scale - 1f) * h / 2f,
              "alpha" to (face?.pageAlpha ?: 1f), "blurDp" to (face?.pageBlur ?: 0f)),
              mapOf("name" to "sorgente", "alpha" to (face?.sourceAlpha ?: 0f))))
        }
        recorded += GlassTraceFrame(glassTraceTimeMs(k, 120), elements)
      }
      assertFalse("viaggio incompleto: $scene", state.isMoving)
      assertEquals(if (cancel) 1 else 0, state.upper)
      return GlassTrace("$scene-${if (dark) "scuro" else "chiaro"}", "f6d", 1f,
        if (dark) "dark" else "light", w, h, recorded, 120, head(scene, dark))
    } finally { dispatcher.dispose(); state.releaseSources(); scope.cancel() }
  }

  private fun head(scene: String, dark: Boolean): Map<String, Any?> {
    val ink = if (dark) "#F2F4FA" else "#202A3E"
    val subtle = if (dark) "#D6DFED" else "#495B73"
    fun text(s: String, x: Int, y: Int, size: Int, color: String = ink) =
      mapOf("kind" to "text", "text" to s, "x" to x, "y" to y, "sizeDp" to size, "color" to color)
    val zoom = scene == "zoom-scurisce"
    val listItems = mutableListOf(text(if (zoom) "Raccolte" else "Archivio", 20, 62, 34),
      text("TACCUINO DI VIAGGIO", 22, 93, 12, subtle))
    if (zoom) {
      listItems += text("I luoghi", 24, 163, 20)
      listItems += text("Verso il lago", 190, 239, 21)
      listItems += text("Sette appunti", 190, 264, 15, subtle)
    }
    val notes = if (zoom) listOf("Dopo la pioggia", "Il sentiero", "Il vecchio porto")
      else listOf("Verso il lago", "La stazione", "Dopo la pioggia", "Il sentiero", "Ritorno a casa", "Il vecchio porto")
    notes.forEachIndexed { i, name ->
      val y = (if (zoom) 360 else 144) + i * 70
      listItems += text(name, 24, y, 20)
      listItems += text("Note di viaggio   /   ${i + 12} novembre", 24, y+21, 13, subtle)
    }
    val detail = mutableListOf(text("Verso il lago", 22, 68, 32), text("12 NOVEMBRE", 24, 99, 12, subtle))
    listOf("Il primo treno partiva presto.", "Dalla finestra si vedeva il lago.",
      "Restava appena un filo di nebbia", "fra i tetti bassi e la riva.",
      "Alla stazione il bar era aperto.", "Ho segnato il nome del paese", "sulla prima pagina del taccuino.").forEachIndexed { i, line ->
      detail += text(line, 24, 165 + i * 36, 19)
    }
    fun shape(color: String, r: Rect = Rect(0f, 0f, w, h)): Map<String, Any?> = mapOf("kind" to "shape",
      "x" to r.left, "y" to r.top, "w" to r.width, "h" to r.height, "color" to color, "radius" to 0)
    val reference = when (scene) {
      "pagina-arriva" -> "Note 26 WWDC25 284 f22230–52 e f22611–23; Impostazioni F06/F07. Scuro NV."
      "pagina-col-dito" -> "Note 26 WWDC25 284 f23463–23542, F04. Scuro NV."
      "zoom-scurisce" -> "iPadOS 18 WWDC24 10145 f958–966 e f3183–3200, ripresa a 0,5×. Scuro NV."
      else -> "Annullo Apple NV. Confronto interno sulla stessa posizione disegnata."
    }
    return mapOf("background" to mapOf("kind" to "color", "color" to "#427873"),
      "_confronto" to reference, "_ripetizioni" to 3,
      "base" to mapOf("lista" to shape(if (dark) "#606060" else "#F0F0F0") + mapOf("content" to listOf(mapOf("name" to "testo", "items" to listItems))),
        "cella" to shape(if (dark) "#303D53" else "#DEE7F4", card) + mapOf("content" to listOf(mapOf("name" to "titolo", "label" to "Lago", "color" to ink))),
        "velo" to shape("#00000000"), "pagina" to shape(if (dark) "#303D53" else "#DEE7F4") +
          mapOf("content" to listOf(mapOf("name" to "inchiostro", "items" to detail),
            mapOf("name" to "sorgente", "label" to "Lago", "color" to ink)))))
  }
}
