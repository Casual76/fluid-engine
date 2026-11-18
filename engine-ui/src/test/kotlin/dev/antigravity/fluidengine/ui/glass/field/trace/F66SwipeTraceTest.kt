package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeCapsuleGapDp
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeCapsuleInsetDp
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeElasticDp
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeHeldVeil
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeLabelGapDp
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeRestLookDp
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeStyle
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeAppearAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeAppearSpec
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeCapsuleGeometry
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeCapsuleHeightPx
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeCapsuleSlot
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeExitSpec
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeGlyphPx
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeOffsetPx
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeSettleSpec
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeThresholdPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Le tracce del movimento delle azioni a scorrimento (F6.6): la riga di un gruppo presa dal dito, la capsula che nasce e
 * si allunga, il rilascio. Le grandezze vengono dalle stesse funzioni pure del componente (`FluidSwipeActions.kt`), in
 * dp (densita' 1 nei conti). **Il dito e' quello di Apple**: lo spazio aperto dalla riga nella lista della sessione 269,
 * misurato fotogramma per fotogramma (f32209-f32223, un punto ogni 33,3 ms, in pt), su una riga larga come sul tablet
 * (520 dp: la soglia e' il tetto di 180 dp, e il dito di Apple, che arriva a 178, non la passa). Si scrivono con
 * `GLASS_F66_DIR` (di solito `tools/glassbench/traces/f6-6`), si rendono con `glassmotion.py`; la striscia di Apple da
 * mettere accanto e' `sessioni/f6-6/apple/L3_lista_f32209-32245_strip.jpg`.
 *
 * - `azione-apre-e-torna`: il dito di Apple, lasciato dove Apple lo lascia (467 ms): torna con la molla critica (Apple
 *   invece resta aperta: e' la divergenza del piano);
 * - `azione-esce`: lo stesso dito che prosegue oltre la soglia fino a 240 dp e lascia con una spinta: esce, e la capsula
 *   riempie la riga che se ne va;
 * - `azione-com-era`: il dito della prima con l'aspetto di prima ([FluidSwipeStyle.Fill]), per il confronto.
 */
class F66SwipeTraceTest {
  private val fps = 60
  private val width = 552f
  private val rowWidth = 520f

  /** Lo spazio aperto dal dito di Apple, in pt, ogni 33,3 ms da f32209 (bordo della riga presa, lista, 2,149 px/pt). */
  private val appleReveal = floatArrayOf(0f, 33.5f, 61f, 84f, 101f, 116f, 131f, 142f, 158f, 168f, 173f, 174.5f, 177f, 177f, 178f)
  private val appleReleaseMs = 467.0
  private val rowHeight = 72f
  private val left = 16f
  private val top = 24f
  private val red = Color(0xFFFF3B30)
  private val surface = Color(0xFFFFFFFF)
  private val ink = Color(0xFF1B1B1F)
  private val gray = Color(0xFF6C6C72)
  private val neutral = Color(0xFFE3E3E8)
  private val paper = mapOf("background" to mapOf("kind" to "color", "color" to "#F2F2F7"))

  @Test fun `esporta la riga che si apre e torna, quella che esce e l'aspetto di prima`() {
    val back = trace("azione-apre-e-torna", FluidSwipeStyle.Capsule, commit = false)
    val exit = trace("azione-esce", FluidSwipeStyle.Capsule, commit = true)
    val old = trace("azione-com-era", FluidSwipeStyle.Fill, commit = false)
    for (t in listOf(back, exit, old)) {
      GlassTraceOutput.requestedDir(System.getenv("GLASS_F66_DIR"))?.let { t.write(File(it, "${t.name}/trace.json")) }
    }
    // La riga che torna arriva a casa e ci resta; quella che esce arriva fuori dalla riga.
    assertEquals(left, back.frames.last().elements.single { it["name"] == "riga" }["x"] as Float, 0.01f)
    assertTrue((exit.frames.last().elements.single { it["name"] == "riga" }["x"] as Float) <= left - rowWidth + 0.5f)
  }

  private fun spring(spec: AnimationSpec<Float>, from: Float, to: Float, velocity: Float = 0f) =
    TargetBasedAnimation(spec, Float.VectorConverter, from, to, velocity)

  /**
   * Il dito: lo spazio di Apple, interpolato fra i suoi fotogrammi; con [beyond] prosegue dopo l'ultimo fino a 240 dp
   * a 600 ms (oltre la soglia).
   */
  private fun fingerAt(ms: Double, beyond: Boolean): Float {
    val step = 1000.0 / 30.0
    val last = appleReveal.size - 1
    if (ms >= last * step) {
      if (!beyond) return appleReveal[last]
      val t = ((ms - last * step) / (600.0 - last * step)).coerceIn(0.0, 1.0).toFloat()
      return appleReveal[last] + (240f - appleReveal[last]) * t
    }
    val i = (ms / step).toInt().coerceIn(0, last - 1)
    val f = ((ms - i * step) / step).toFloat()
    return appleReveal[i] + (appleReveal[i + 1] - appleReveal[i]) * f
  }

  private fun trace(name: String, style: FluidSwipeStyle, commit: Boolean): GlassTrace {
    val threshold = fluidSwipeThresholdPx(rowWidth, 1f)
    val releaseMs = if (commit) 600.0 else appleReleaseMs
    val appear = spring(fluidSwipeAppearSpec(), 0f, 1f)
    val lift = spring(FluidMotion.flick(), 0f, 1f)
    // Il rilascio: con la spinta del dito che sta ancora andando (esce) o fermo (torna).
    var releaseOffset = 0f
    var armedAt = Double.NaN
    var restAt = Double.NaN
    val frames = (-3..96).map { index ->
      val ms = glassTraceTimeMs(index, fps)
      val raw = if (ms <= 0.0) 0f else -fingerAt(ms, beyond = commit)
      val dragged = fluidSwipeOffsetPx(raw, threshold, FluidSwipeElasticDp, hasStart = false, hasEnd = true, reducedMotion = false)
      if (abs(dragged) >= threshold && armedAt.isNaN()) armedAt = ms
      val offset = if (ms <= releaseMs) {
        releaseOffset = dragged
        dragged
      } else {
        val after = ((ms - releaseMs) * 1_000_000.0).toLong()
        if (commit) {
          spring(fluidSwipeExitSpec(style), releaseOffset, -rowWidth, -600f).getValueFromNanos(after)
        } else {
          spring(fluidSwipeSettleSpec(style), releaseOffset, 0f).getValueFromNanos(after)
        }
      }
      val since = (ms.coerceAtLeast(0.0) * 1_000_000.0).toLong()
      // Come il componente: la riga che torna si lascia a FluidSwipeRestLookDp da casa (angoli e velo col lampo).
      if (!commit && ms > releaseMs && restAt.isNaN() && abs(offset) <= FluidSwipeRestLookDp) restAt = ms
      val resting = !restAt.isNaN()
      val appearNow = if (ms <= 0.0 || resting) 0f else appear.getValueFromNanos(since)
      val liftNow = when {
        ms <= 0.0 -> 0f
        resting -> 1f - spring(FluidMotion.flick(), 0f, 1f).getValueFromNanos(((ms - restAt) * 1_000_000.0).toLong())
        else -> lift.getValueFromNanos(since)
      }.coerceIn(0f, 1f)
      val armNow = if (armedAt.isNaN() || ms < armedAt) 0f else
        spring(FluidMotion.flick(), 0f, 1f).getValueFromNanos(((ms - armedAt) * 1_000_000.0).toLong())
      GlassTraceFrame(ms, elements(style, offset, appearNow, liftNow, armNow))
    }
    return GlassTrace(name, "f6-6", 3f, "light", width, top * 2f + rowHeight * 2f, frames, fps, head = paper + linkedMapOf(
      "events" to listOf(mapOf("t" to releaseMs, "kind" to "mark", "label" to "rilascio")),
      "probes" to listOf(
        mapOf("name" to "bordo-riga", "kind" to "value", "path" to "riga.x"),
      ),
      "_" to "F6.6: dal Kotlin di FluidSwipeActions.kt; Apple in artifacts/liquid-glass/sessioni/f6-6/apple/",
    ))
  }

  private fun elements(style: FluidSwipeStyle, offset: Float, appear: Float, lift: Float, armed: Float): List<Map<String, Any?>> {
    val out = mutableListOf<Map<String, Any?>>()
    // Il gruppo: due righe bianche, angoli del gruppo, il separatore sotto il testo della prima.
    out += mapOf("name" to "gruppo", "kind" to "shape", "x" to left, "y" to top, "w" to rowWidth, "h" to rowHeight * 2f,
      "radius" to FluidRadius.Group.value, "color" to glassColor(surface))
    out += mapOf("name" to "separatore", "kind" to "shape", "x" to left + 64f, "y" to top + rowHeight - 0.5f,
      "w" to rowWidth - 64f, "h" to 0.5f, "radius" to 0, "color" to glassColor(ink.copy(alpha = 0.10f)))
    out += row("seconda", top + rowHeight, 0f, 0f, "Banca", "fluid:account_balance|circle")
    if (offset != 0f) {
      if (style.isCapsuleStyle()) out += capsule(offset, appear, armed) else out += fill(offset, armed)
    }
    // L aspetto di prima non prende la riga: niente angoli, niente velo.
    out += row("riga", top, offset, if (style.isCapsuleStyle()) lift else 0f, "Marta", "fluid:mark_email_unread|circle")
    return out
  }

  private fun FluidSwipeStyle.isCapsuleStyle() = this == FluidSwipeStyle.Capsule

  /** Una riga: il suo fondo (preso: arrotondato e velato) e il contenuto, ritagliati dalla sagoma del gruppo. */
  private fun row(name: String, y: Float, offset: Float, lift: Float, title: String, icon: String): Map<String, Any?> {
    val held = ink.copy(alpha = FluidSwipeHeldVeil).compositeOver(surface)
    val x = left + offset
    return mapOf("name" to name, "kind" to "shape", "x" to x, "y" to y, "w" to rowWidth, "h" to rowHeight,
      "radius" to FluidRadius.Group.value * lift, "color" to glassColor(lerp(surface, held, lift)),
      "drawClip" to listOf(listOf(left - x, 0f, left - x + rowWidth, rowHeight)),
      "content" to listOf(
        mapOf("name" to "icona", "x" to 16, "y" to 20, "w" to 32, "h" to 32, "icon" to icon, "iconSizeDp" to 19,
          "color" to "#0A65CB"),
        mapOf("name" to "titolo", "x" to 64, "y" to 14, "w" to 200, "h" to 24, "label" to title, "labelSizeDp" to 17,
          "labelWeight" to 600, "color" to glassColor(ink)),
        mapOf("name" to "sottotitolo", "x" to 64, "y" to 38, "w" to 240, "h" to 20, "label" to "Domani alle 9",
          "labelSizeDp" to 15, "color" to glassColor(gray)),
      ))
  }

  /** La capsula di iOS 27 nello spazio aperto: le stesse funzioni del disegno del componente. */
  private fun capsule(offset: Float, appear: Float, armed: Float): List<Map<String, Any?>> {
    val slot = fluidSwipeCapsuleSlot(offset, rowWidth, FluidSwipeCapsuleGapDp, FluidSwipeCapsuleInsetDp) ?: return emptyList()
    val labelDp = 14f
    val geometry = fluidSwipeCapsuleGeometry(slot.start, slot.endInclusive, rowHeight,
      fluidSwipeCapsuleHeightPx(rowHeight, 1f), FluidSwipeLabelGapDp + labelDp, appear, armed) ?: return emptyList()
    val alpha = fluidSwipeAppearAlpha(appear)
    val c = geometry.capsule
    val glyph = fluidSwipeGlyphPx(fluidSwipeCapsuleHeightPx(rowHeight, 1f), 1f) * geometry.scale
    val labelInk = lerp(gray, red, armed)
    return listOf(
      mapOf("name" to "capsula", "kind" to "shape", "x" to left + c.left, "y" to top + c.top,
        "w" to c.width, "h" to c.height, "radius" to "capsule", "color" to glassColor(red.copy(alpha = alpha)),
        "content" to listOfNotNull(
          if (c.width > glyph) mapOf("name" to "glifo", "icon" to "fluid:delete|circle", "iconSizeDp" to glyph,
            "color" to "#FFFFFF", "alpha" to alpha) else null,
        )),
      // L'etichetta sotto, nella sua lastra trasparente: cresce con la capsula.
      mapOf("name" to "etichetta", "kind" to "shape", "x" to left + c.center.x - 40f,
        "y" to top + c.bottom + FluidSwipeLabelGapDp * geometry.scale, "w" to 80,
        "h" to (labelDp * geometry.scale).coerceAtLeast(1f), "radius" to 0, "color" to "#00000000",
        "content" to listOf(mapOf("name" to "testo", "label" to "Elimina",
          "labelSizeDp" to (11f * geometry.scale).coerceAtLeast(1f), "labelWeight" to 500,
          "color" to glassColor(labelInk), "alpha" to alpha))),
    )
  }

  /** L'aspetto di prima: lo spazio pieno del colore (grigio fino alla soglia), l'icona sul bordo. */
  private fun fill(offset: Float, armed: Float): List<Map<String, Any?>> {
    val revealed = abs(offset)
    return listOf(mapOf("name" to "spazio", "kind" to "shape", "x" to left + rowWidth - revealed, "y" to top,
      "w" to revealed, "h" to rowHeight, "radius" to 0, "color" to glassColor(lerp(neutral, red, armed)),
      "drawClip" to listOf(listOf(0f, 0f, revealed, rowHeight)),
      "content" to listOf(mapOf("name" to "icona", "x" to revealed - 20f - 24f, "y" to 24, "w" to 24, "h" to 24,
        "icon" to "fluid:delete|circle", "iconSizeDp" to 24, "color" to glassColor(lerp(gray, Color.White, armed)))),
    ))
  }
}
