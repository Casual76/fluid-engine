package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdownState
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import org.junit.Assert.assertTrue
import org.junit.Test

/** Banco dei fotogrammi. La prova della navigazione e dei nodi è in FluidUndoCountdownUiTest. */
class FluidUndoCountdownTraceTest {
  @Test fun exportSendUndoAndExpiry() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_UNDO_TRACE_DIR"))
    val traces = listOf(
      trace("invia-composto", sends = true, cancels = false),
      trace("annulla-contatore", sends = false, cancels = true),
      trace("annulla-scade", sends = false, cancels = false),
    )
    if (output != null) traces.forEach { it.write(GlassTraceOutput.fileFor(output, it)) }
  }

  private fun trace(name: String, sends: Boolean, cancels: Boolean): GlassTrace {
    val fps = 120
    val width = 360f
    val height = 260f
    val sendAt = 100L
    val startsAt = if (sends) 470L else 100L
    val duration = 8_000L
    val endsAt = startsAt + if (cancels) 3_000L else duration
    val through = endsAt + 450L
    val enter = GlassContentJourney.start("new-mail", "undo")
    val exit = GlassContentJourney.start("undo", "new-mail")
    val openWidth = TodaySpring(FluidMotion.snappy(), 44f, 96f)
    val closeWidth = TodaySpring(FluidMotion.snappy(), 96f, 44f)
    val pageReturn = TodaySpring(FluidPageMotion.pageReturn(), 0f, 1f)
    val pagePush = TodaySpring(FluidPageMotion.page(), 1f, 0f)
    var now = 0L
    val window = FluidUndoCountdownState(FluidNotificationCountdown(duration, startsAt), { now })
    val ink = Color(0xFF174B47)
    var blurredFrames = 0

    val frames = (0..(through * fps / 1_000L).toInt()).map { i ->
      val t = glassTraceTimeMs(i, fps)
      val nanos = glassTraceTimeNanos(i, fps)
      now = nanos / 1_000_000L
      val elapsed = (nanos - startsAt * 1_000_000L).coerceAtLeast(0L)
      val closing = now >= endsAt
      val sinceClose = (nanos - endsAt * 1_000_000L).coerceAtLeast(0L)
      val w = when {
        now < startsAt -> 44f
        closing -> closeWidth.value(sinceClose)
        else -> openWidth.value(elapsed)
      }
      val x = width - 14f - w
      val face = when {
        now < startsAt -> GlassContentJourney.rest("new-mail").frame(t)
        closing -> exit.frame(sinceClose / 1_000_000.0)
        else -> enter.frame(elapsed / 1_000_000.0)
      }
      val fill = window.fill()
      val layers = face.layers.map { layer ->
        val radius = glassContentBlurRadiusPx(glassContentSigmaPx(layer.look.blur, 24f))
        if (layer.look.alpha > 0f && radius > 0.1f) blurredFrames++
        if (layer.key == "undo") {
          val items = listOf(
            mapOf("kind" to "rect", "x" to 0f, "y" to 0f, "w" to w * fill, "h" to 44f, "color" to "#267D7240"),
            word("Annulla", 23f, 28f, 16f),
          )
          linkedMapOf<String, Any?>("name" to "annulla", "items" to items, "alpha" to layer.look.alpha,
            "blurDp" to radius, "scale" to layer.look.scale)
        } else {
          todayIconLayer("nuova-mail", Rect(x, 18f, x + w, 62f), "fluid:add|plus", 24f, ink, Offset(x, 18f),
            1f, alpha = layer.look.alpha, scaleX = layer.look.scale) + ("blurDp" to radius)
        }
      }
      val legible = layers.count { (it["alpha"] as Number).toFloat() >= 0.5f && (it["blurDp"] as Number).toFloat() <= 0.7f }
      assertTrue("$name, $t ms, due facce leggibili", legible <= 1)
      val element = mapOf("kind" to "glass", "name" to "tasto", "role" to "Interactive", "x" to x, "y" to 18f,
        "w" to w, "h" to 44f, "radius" to "capsule", "content" to layers, "contentClip" to "shape")
      val composerLeft = when {
        sends && now < sendAt -> 0f
        sends && now < startsAt -> width * pageReturn.value(nanos - sendAt * 1_000_000L).coerceIn(0f, 1f)
        cancels && closing -> width * pagePush.value(sinceClose).coerceIn(0f, 1f)
        else -> width
      }
      val composer = mapOf("kind" to "glass", "name" to "compositore", "role" to "Content",
        "x" to composerLeft, "y" to 0f, "w" to width, "h" to height, "radius" to 0f,
        "content" to listOf(mapOf("name" to "testo", "items" to listOf(
          word("Nuova mail", 20f, 52f, 22f), word("Invia", 286f, 48f, 15f), word("Ci vediamo domani.", 20f, 120f, 17f),
        ))))
      GlassTraceFrame(t, if (composerLeft < width) listOf(element, composer) else listOf(element))
    }
    assertTrue(blurredFrames > 12)
    return GlassTrace(name, "f6b-4-contatore", 1f, "light", width, height, frames, fps,
      head = mapOf(
        "background" to mapOf("kind" to "color", "color" to "#EAF1F3"),
        "page" to listOf(word("Posta in arrivo", 18f, 48f, 22f), word("Riunione di domani", 20f, 118f, 16f),
          word("Foto della gita", 20f, 156f, 16f), word("Appunti", 20f, 194f, 16f)),
      ))
  }

  private fun word(text: String, x: Float, y: Float, size: Float) =
    mapOf("kind" to "text", "text" to text, "x" to x, "y" to y, "sizeDp" to size, "color" to "#174B47")
}
