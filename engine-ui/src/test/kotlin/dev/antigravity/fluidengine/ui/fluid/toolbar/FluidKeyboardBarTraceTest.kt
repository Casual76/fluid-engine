package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidTokenSuggestion
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.trace.F38aTraces
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidKeyboardBarTraceTest {
  @Test fun benchTracesFollowImeAndExchangeContextAt120Hz() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B9_DIR"))
    for (late in listOf(false, true)) for (dark in listOf(false, true)) {
      val trace = traceKeyboard(late, dark)
      assertEquals(120, trace.fps)
      assertTrue(trace.frames.any { frame ->
        frame.elements.any { it["kind"] == "material" } && frame.elements.any { it["name"] == "ime-finto" }
      })
      if (output != null) trace.write(GlassTraceOutput.fileFor(output, trace))
    }
  }

  private fun traceKeyboard(late: Boolean, dark: Boolean): GlassTrace {
    val width = 400f
    val height = 420f
    val planeHeight = 120f
    val planeTop = 38f
    val density = 2f
    val rowHeight = fluidBottomBarMetrics().buttonWidthDp
    val changeAt = 425.0
    val before = resolveKeyboard("destinatari") {
      suggestions(listOf(FluidTokenSuggestion("anna", "Anna"), FluidTokenSuggestion("luca", "Luca"))) {}
    }
    val after = resolveKeyboard("formattazione") {
      button("bold", FluidIcons.FormatBold, "Grassetto", {})
      button("italic", FluidIcons.FormatItalic, "Corsivo", {})
      button("underline", FluidIcons.FormatUnderlined, "Sottolineato", {})
    }
    val memory = FluidToolbarMemory()
    fun plane(resolved: FluidToolbarResolved): GlassPlaneState {
      memory.update(resolved, FluidToolbarMotion.Full, ridesEdge = true)
      val pieces = resolved.groups.map { group ->
        val id = memory.glassIds.getValue(group.id)
        val bounds = Rect(group.leftDp * density, planeTop * density,
          (group.leftDp + group.widthDp) * density, (planeTop + rowHeight) * density)
        GlassPlanePiece(id.toString(), bounds, FluidCornerRadii.all(bounds.height / 2), id = id,
          contentKey = fluidToolbarGlassContentKey(group))
      }
      return GlassPlaneState(pieces, resolved.metrics.groupSpacingDp * density,
        Rect(0f, 0f, width * density, planeHeight * density))
    }
    val initial = plane(before)
    val destination = plane(after)
    assertEquals(initial.pieces.single().id, destination.pieces.single().id)
    val mode = if (late) "tastiera-in-ritardo" else "tastiera-contesto"
    val name = if (dark) "$mode-scuro" else mode
    val background = mapOf("kind" to "color", "color" to if (dark) "#17191E" else "#F0F3F8")
    val base = F38aTraces.build(F38aTraces.Scene(
      name, "Barra dal modello Kotlin e dal piano di produzione, con inset IME sintetici",
      width, planeHeight, dark, initial, listOf(F38aTraces.Step(changeAt, destination)),
      emptyMap(), lastFrame = 108, density = density, phase = "f6b-9", background = background,
    ))
    val journey = GlassContentJourney.rest(before).retarget(changeAt, after)
    val interpolation = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val spec = FluidMotion.standard<Float>()
    val spring = FloatSpringSpec(spec.dampingRatio, spec.stiffness, 1f)
    val fullIme = 192
    val lateAt = 116.66666666666667
    val pageInk = if (dark) "#E7E9F0" else "#1B2130"
    val fieldColor = if (dark) "#2B3240" else "#DCE4F0"
    val keyboardColor = if (dark) "#3D424D" else "#C9D0DE"
    val keyColor = if (dark) "#616978" else "#F7F9FE"

    var followingPlatform = false
    val frames = base.frames.map { frame ->
      val time = frame.tMs
      val imeProgress = interpolation.transform((time / 334.0).toFloat().coerceIn(0f, 1f))
      val measuredIme = if (late) {
        if (time < lateAt) 0 else fullIme
      } else {
        (fullIme * imeProgress).roundToInt()
      }
      val fallbackHeight = if (time < lateAt) 0 else
        spring.getValueFromNanos(((time - lateAt) * 1_000_000.0).toLong(), 0f, fullIme.toFloat(), 0f).roundToInt()
      val sample = if (late) KeyboardImeSample(measuredIme, measuredIme, measuredIme) else
        KeyboardImeSample(measuredIme, 0, fullIme)
      val barInset = sample.layoutBottom(followingPlatform, reduced = false, springBottom = fallbackHeight)
      followingPlatform = followingPlatform || sample.hasIntermediateHeight
      val baseY = height - FluidKeyboardBarHeight.value - barInset
      val focusOnEditor = time >= changeAt
      val visible = fluidKeyboardBarVisible(false, true, true, if (late) measuredIme > 0 else time >= 0.0, false)
      // I campi e i tasti dell'IME sono schematici. Il finto IME viene disegnato per ultimo, fuori dalla cattura.
      val elements = mutableListOf(
        block("destinatari", 16f, 22f, width - 32f, 44f, fieldColor, 18f, "Destinatari", pageInk),
        block("messaggio", 16f, 78f, width - 32f, 44f, fieldColor, 18f, "Messaggio", pageInk),
        block("fuoco", 24f, if (focusOnEditor) 93f else 37f, 3f, 14f, "#5389DF", 1f),
      )
      val inks = journey.frame(time).layers.flatMap { layer ->
        val resolved = layer.key as FluidToolbarResolved
        resolved.elements.filterNot { it.inOverflow }.map { item ->
          val glyph = item.item.symbol as? ImageVector
          val icon = glyph?.name?.substringAfter('.')?.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")?.lowercase(Locale.ROOT)
          val blur = glassContentBlurRadiusPx(glassContentSigmaPx(
            layer.look.blur, fluidToolbarIconSize(item.item.role).value,
          ))
          val face = linkedMapOf<String, Any?>(
            "name" to "${layer.id}-${item.item.id}", "x" to item.leftDp, "y" to planeTop,
            "w" to item.widthDp, "h" to rowHeight, "alpha" to layer.look.alpha,
            "scale" to layer.look.scale, "blurDp" to blur, "color" to pageInk, "marginDp" to 12,
          )
          if (icon != null) {
            face["icon"] = "fluid:$icon"
            face["iconSizeDp"] = fluidToolbarIconSize(item.item.role).value
          } else {
            face["label"] = item.item.label
            face["labelSizeDp"] = 17
          }
          face
        }
      }
      for (field in frame.elements.takeIf { visible }.orEmpty()) {
        val x = (field.getValue("x") as Number).toFloat()
        val y = (field.getValue("y") as Number).toFloat()
        elements += field + mapOf(
          "y" to (y - planeTop + baseY),
          "contentClip" to "alpha",
          "content" to inks.map { it + mapOf(
            "x" to (it.getValue("x") as Number).toFloat() - x,
            "y" to (it.getValue("y") as Number).toFloat() - y,
          ) },
        )
      }
      if (measuredIme > 0) {
        val keyboardTop = height - measuredIme
        elements += block("ime-finto", 0f, keyboardTop, width, measuredIme.toFloat(), keyboardColor, 0f)
        for ((row, keys) in listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").withIndex()) {
          for ((column, key) in keys.withIndex()) {
            elements += block("key-$row-$column", (width - keys.length * 36f) / 2 + column * 36f,
              keyboardTop + 14f + row * 44f, 32f, 34f, keyColor, 7f, key.toString(), pageInk)
          }
        }
      }
      GlassTraceFrame(time, elements)
    }
    return GlassTrace(name, "f6b-9", density, base.theme, width, height, frames, base.fps, mapOf("background" to background))
  }

  private fun block(
    name: String, x: Float, y: Float, w: Float, h: Float, color: String, radius: Float,
    label: String? = null, ink: String = "#000000",
  ): Map<String, Any?> = buildMap {
    put("kind", "shape")
    put("name", name)
    put("x", x)
    put("y", y)
    put("w", w)
    put("h", h)
    put("radius", radius)
    put("color", color)
    if (label != null) put("content", listOf(mapOf("x" to 0, "y" to 0, "w" to w, "h" to h,
      "name" to "$name-label", "label" to label, "labelSizeDp" to 14, "color" to ink)))
  }
}
