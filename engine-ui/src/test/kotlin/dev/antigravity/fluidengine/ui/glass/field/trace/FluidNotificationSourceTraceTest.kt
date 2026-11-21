package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidModalSourceMotion
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidPopoverPeakOvershoot
import dev.antigravity.fluidengine.ui.fluid.FluidPopoverWindowPopGain
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.GlassContentTiming
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.NotificationExitMillis
import dev.antigravity.fluidengine.ui.fluid.fluidMenuInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLensBlurDp
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReturnInkSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidModalFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalFocusSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalGeometrySpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalReturnFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalSourceSlab
import dev.antigravity.fluidengine.ui.fluid.fluidModalTravelClip
import dev.antigravity.fluidengine.ui.fluid.fluidNotificationSourceFrame
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il banco a 120 Hz usa le specifiche, il posizionamento, la sagoma e la lente dell'engine.
 * Titoli e glifi sono gli elementi del banco, in luogo dell'albero Compose.
 */
class FluidNotificationSourceTraceTest {
  private val room = Size(360f, 400f)
  private val motion = FluidModalSourceMotion.Popover
  private val fps = 120

  private data class Scene(
    val name: String,
    val bottom: Boolean = false,
    val detach: Boolean = false,
    val returns: Boolean = false,
    val composed: Boolean = false,
    val dark: Boolean = false,
  )

  @Test fun exportTheFourNotificationJourneys() {
    val scenes = listOf(
      Scene("avviso-dal-tasto"),
      Scene("avviso-annulla-torna", bottom = true, returns = true),
      Scene("avviso-sorgente-sparita", detach = true),
      Scene("invia-composto", composed = true, returns = true),
      Scene("invia-composto-scuro", composed = true, returns = true, dark = true),
      Scene("avviso-annulla-torna-scuro", bottom = true, returns = true, dark = true),
    )
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B4_TRACE_DIR"))
    scenes.forEach { scene ->
      val trace = trace(scene)
      if (output != null) trace.write(GlassTraceOutput.fileFor(output, trace))
    }
  }

  private fun trace(scene: Scene): GlassTrace {
    val anchor = if (scene.bottom) Rect(270f, 330f, 344f, 378f) else Rect(270f, 22f, 344f, 70f)
    val cardSize = Size(316f, 117f)
    val target = fluidNotificationSourceFrame(anchor, cardSize, room, 12f, 10f)
    val start = FluidForm.Slab(anchor, FluidCornerRadii.all(24f))
    var end = FluidForm.Slab(target, FluidCornerRadii.all(20f))
    val state = FluidPhysicsState(start)
    val driver = TodayLiquidDriver(state, 1f)
    val optics = GlassDefaults.panelOptics(overSharpPage = true).sanitized()
    val dress = LiquidPhysicsDress(GlassRole.Modal, PhysicsMaterialSource(optics, optics), 1f, shadow = true, darkSide = scene.dark)
    val enter = TodaySpring(fluidModalGeometrySpec(motion, true, 0f), 0f, 1f)
    val enterFocus = TodaySpring(fluidModalFocusSpec(motion, true, 0f, fresh = true), 0f, 1f)
    val exit = TodaySpring(fluidModalGeometrySpec(motion, false, 1f), 1f, 0f)
    val exitFocus = TodaySpring(fluidModalFocusSpec(motion, false, 1f, fresh = false), 1f, 0f)
    val exitInk = TodaySpring(fluidMenuReturnInkSpec(1f), 1f, 0f)
    val slide = TodaySpring(FluidMotion.pixels(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy), 0f, -cardSize.height / 4f)
    val fade = TodaySpring(FluidMotion.fadeOut(NotificationExitMillis), 1f, 0f)
    val inflation = fluidMenuInflation(target.width, target.height, room.width, 6f, 240f,
      FluidPopoverWindowPopGain, FluidPopoverPeakOvershoot)
    val openingMs = if (scene.composed) 650L else 75L
    val leavingMs = if (scene.composed) 6_150L else if (scene.returns || scene.detach) 650L else 1_000L
    val untilMs = if (scene.returns || scene.detach) leavingMs + 380L else 550L
    val ink = if (scene.dark) Color(0xFFF4F6FC) else Color(0xFF111A2F)
    val line = if (scene.dark) "#9CC1FC" else "#275EAC"
    val countdown = FluidNotificationCountdown(4_000L, openingMs)
    val update = GlassContentJourney.start(false, true, GlassContentTiming())
    var blurredInkFrames = 0
    var readableAt = Double.NaN
    var clock = 0f

    val frames = (0..(untilMs * fps / 1_000L).toInt()).map { index ->
      val n = glassTraceTimeNanos(index, fps)
      val ms = glassTraceTimeMs(index, fps)
      val entered = (n - openingMs * 1_000_000).coerceAtLeast(0L)
      val left = (n - leavingMs * 1_000_000).coerceAtLeast(0L)
      val returning = ms >= leavingMs && scene.returns
      val fallback = ms >= leavingMs && scene.detach
      val travel = if (returning) exit.value(left) else enter.value(entered)
      clock = travel
      val focus = if (returning) exitFocus.value(left) else enterFocus.value(entered)
      val visibility = when {
        returning -> exitInk.value(left)
        fallback -> fade.value(left)
        else -> 1f
      }
      val sourceExists = !scene.detach || entered < 75_000_000L
      val updateAt = ms - openingMs - countdown.durationMillis
      val sent = scene.composed && updateAt >= 0.0
      val panel = target.translate(0f, if (fallback) slide.value(left) else 0f)
      end = end.copy(frame = panel)
      state.driveExternally(start, end, { clock }, startFrame = null,
        overshootInflationX = inflation.x, overshootInflationY = inflation.y,
        travelSlab = { if (travel < 1f) fluidModalSourceSlab(start, end, travel, returning) else null })
      driver.frame(n)
      val plan = state.ensurePlan()
      val bounds = state.contentBoundsNow()
      val face = when {
        !sourceExists -> 0f
        returning -> fluidModalReturnFaceAlpha(travel, bounds, anchor)
        else -> fluidModalFaceAlpha(motion, travel)
      }
      val alpha = fluidModalContentAlpha(motion, travel, focus, visibility)
      assertEquals("tasto e avviso a ${scene.name}, $ms ms", 0f, face * alpha, 0f)
      val blur = fluidMenuLensBlurDp(focus)
      if (alpha > 0f && blur > 0.1f) blurredInkFrames++
      if (readableAt.isNaN() && alpha >= 0.5f && blur <= 1.6f) readableAt = ms - openingMs

      val contents = buildList<Map<String, Any?>> {
        if (face > 0f) {
          val glyph = if (!scene.composed || ms < 350.0) "fluid:send|send" else "fluid:check|check"
          val pose = if (returning) anchor else bounds
          add(todayIconLayer("tasto", pose, glyph, 22f, ink, Offset.Zero, 1f, face,
            minOf(pose.width / anchor.width, pose.height / anchor.height)))
        }
        if (alpha > 0f) {
          val map = fluidModalContentMap(state, panel, travel, focus)
          val center = panel.center + map.translation
          val sx = center.x - panel.width * map.scale / 2f
          val sy = center.y - panel.height * map.scale / 2f
          fun words(confirmed: Boolean) = listOf(
            word(if (confirmed) "Inviata" else "Invio in corso", sx + 22 * map.scale, sy + 35 * map.scale,
              17 * map.scale, glassColor(ink)),
            word(if (confirmed) "Il messaggio è stato inviato." else "Puoi annullare l'invio.", sx + 22 * map.scale,
              sy + 59 * map.scale, 13 * map.scale, glassColor(ink)),
          )
          fun inkLayer(name: String, items: List<Map<String, Any?>>, amount: Float, radius: Float) =
            linkedMapOf<String, Any?>(
              "name" to name, "x" to 0, "y" to 0, "w" to room.width, "h" to room.height,
              "items" to items, "alpha" to amount, "blurDp" to radius,
            )
          if (sent) {
            for (layer in update.frame(updateAt).layers) {
              val confirmed = layer.key as? Boolean ?: continue
              val radius = glassContentBlurRadiusPx(glassContentSigmaPx(layer.look.blur, 24f))
              add(inkLayer("esito-${layer.id}", words(confirmed), alpha * layer.look.alpha, maxOf(blur, radius)))
            }
          } else {
            val wording = words(false) + word("Annulla", sx + 38 * map.scale, sy + 91 * map.scale, 15 * map.scale, line)
            val fraction = countdown.fractionAt(ms.toLong())
            val ring = ringItems(sx + 65f * map.scale, sy + 91f * map.scale, map.scale, fraction, line)
            val layer = inkLayer("avviso", wording + ring, alpha, blur * map.scale)
            menuTraceContentLens(plan, Offset.Zero, 1f, focus)?.let { layer["effect"] = it }
            add(layer)
          }
        }
      }

      val clip = fluidModalTravelClip(state, panel, travel, focus)?.translate(panel.topLeft)
      val material = dress.element("viaggio", state, plan, 1f, Offset.Zero, room.width, room.height, contents, timeMs = ms)
      val element = if (clip == null) material else material + ("contentClip" to listOf(mapOf(
        "x" to round3(clip.left), "y" to round3(clip.top), "w" to round3(clip.width), "h" to round3(clip.height),
        "radius" to listOf(clip.topLeftCornerRadius.x, clip.topRightCornerRadius.x,
          clip.bottomRightCornerRadius.x, clip.bottomLeftCornerRadius.x).map(::round3),
      )))
      val elements = when {
        fallback && visibility == 0f -> emptyList()
        fallback -> listOf(fadeMaterial(element, visibility))
        else -> listOf(element)
      }
      GlassTraceFrame(ms, elements)
    }
    assertTrue("${scene.name}: più fotogrammi di inchiostro sfocato", blurredInkFrames > 3)
    assertTrue("${scene.name}: l'avviso è leggibile durante la posa ($readableAt ms)", readableAt in 140.0..250.0)
    return GlassTrace(scene.name, "f6b-4", 1f, if (scene.dark) "dark" else "light", room.width, room.height, frames, fps,
      head = mapOf(
        "background" to mapOf("kind" to "color", "color" to if (scene.dark) "#151B29" else "#E7EDF9"),
        "page" to listOf(
          word("Posta in arrivo", 18f, 53f, 19f, glassColor(ink)),
          word("Bozza  ·  Ci vediamo domani.", 18f, 232f, 15f, glassColor(ink)),
          word("Archivio", 18f, 270f, 14f, glassColor(ink)),
        ),
      ))
  }

  /** Il renderer del banco usa punti ravvicinati per lo stesso arco di capsula disegnato dalla UI. */
  private fun ringItems(cx: Float, cy: Float, scale: Float, fraction: Float, color: String): List<Map<String, Any?>> {
    if (fraction <= 0f) return emptyList()
    val radius = 16f
    val halfStraight = 26f
    val arc = (PI * radius).toFloat()
    val length = 4 * halfStraight + 2 * arc
    val count = (length * fraction / 1.8f).toInt()
    return (0..count).map { step ->
      val d = step * 1.8f
      val (x, y) = when {
        d <= halfStraight -> d to -radius
        d <= halfStraight + arc -> {
          val angle = (d - halfStraight) / radius - PI / 2
          halfStraight + radius * cos(angle).toFloat() to radius * sin(angle).toFloat()
        }
        d <= 3 * halfStraight + arc -> (2 * halfStraight + arc - d) to radius
        d <= 3 * halfStraight + 2 * arc -> {
          val angle = (d - 3 * halfStraight - arc) / radius + PI / 2
          -halfStraight + radius * cos(angle).toFloat() to radius * sin(angle).toFloat()
        }
        else -> (d - length) to -radius
      }
      mapOf("kind" to "circle", "x" to round3(cx + x * scale), "y" to round3(cy + y * scale),
        "r" to round3(1.1f * scale), "color" to color)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun fadeMaterial(element: Map<String, Any?>, alpha: Float): Map<String, Any?> {
    fun fade(layer: Map<String, Any?>): Map<String, Any?> {
      val uniforms = layer.getValue("uniforms") as Map<String, Any?>
      val amount = (uniforms.getValue("materialize") as Number).toFloat()
      return layer + ("uniforms" to (uniforms + ("materialize" to amount * alpha)))
    }
    val body = fade(element.getValue("body") as Map<String, Any?>)
    val light = (element["light"] as? List<Map<String, Any?>>)?.map(::fade)
    val dimmed = element + ("body" to body)
    return if (light == null) dimmed else dimmed + ("light" to light)
  }

  private fun word(text: String, x: Float, y: Float, size: Float, color: String): Map<String, Any?> =
    mapOf("kind" to "text", "text" to text, "x" to x, "y" to y, "sizeDp" to size, "color" to color)
}
