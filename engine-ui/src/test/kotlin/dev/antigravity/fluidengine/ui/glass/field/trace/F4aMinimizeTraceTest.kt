package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMinimizeState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMemory
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMotion
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarSelectionEasing
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMinimizeLook
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarIconCenterPx
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarReducedFadeProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarSearchGlassProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Stessa corsa e stessi strati del componente, su una pagina fissa del banco. */
class F4aMinimizeTraceTest {
  private val status = 26f
  private val travel = 105f
  private val searchHeight = 52f
  private val width = 360f
  private val lens = FluidMetrics.GlassButtonLens.value
  private val icon = FluidMetrics.BarIconSize.value
  private val fps = 60
  private val paper = mapOf("background" to mapOf("kind" to "color", "color" to "#FAFAFC"))

  @Test fun `esporta ritiro pausa ritorno e variante ridotta`() {
    for (reduced in listOf(false, true)) for (returning in listOf(false, true)) {
      val name = (if (returning) "barra-ritorna" else "barra-si-ritira") + if (reduced) "-ridotto" else ""
      val bar = FluidToolbarMinimizeState(direction = -1f, reducedMotion = reduced)
      bar.measure(travel)
      if (returning) bar.scroll(-travel, canHide = true)
      val finalDragProgress = if (returning) 0.24f else 0.93f
      val dragEnd = if (returning) 21 else 13
      val release = dragEnd + 6
      val settle = TargetBasedAnimation(
        animationSpec = if (reduced) FluidMotion.crossFade() else FluidMotion.gentle(),
        typeConverter = Float.VectorConverter,
        initialValue = finalDragProgress,
        targetValue = if (returning) 0f else 1f,
      )
      val frames = (-2..(release + 36)).map { index ->
        val t = glassTraceTimeMs(index, fps)
        if (index in 1..dragEnd) {
          val fraction = index.toFloat() / dragEnd
          val target = if (returning) 1f - (1f - finalDragProgress) * fraction else finalDragProgress * fraction
          bar.scroll(bar.offsetPx - target * travel, canHide = true)
        }
        val phase = if (index <= release) bar.progress else
          settle.getValueFromNanos(glassTraceTimeNanos(index - release, fps)).coerceIn(0f, 1f)
        frame(t, phase, reduced)
      }
      val held = frames.filter { it.tMs >= glassTraceTimeMs(dragEnd, fps) && it.tMs <= glassTraceTimeMs(release, fps) }
      assertTrue(held.size >= 6 && held.all { it.elements == held.first().elements })
      assertEquals(status + if (returning) travel else 0f,
        frames.last().elements.single { it["name"] == "campo" }["y"] as Float, 0.01f)
      val trace = GlassTrace(name, "f4a-4", 2f, "light", width, 256f, frames, fps, head = paper + linkedMapOf(
        "page" to listOf(mapOf("kind" to "text", "text" to "9:41", "x" to 18, "y" to 7, "sizeDp" to 13)),
        "events" to listOf(mapOf("t" to glassTraceTimeMs(release, fps), "kind" to "mark", "label" to "rilascio")),
        "probes" to listOf(
          probe("opacita-titolo", "titolo.content[testo].alpha"),
          probe("sfocatura-titolo", "titolo.content[testo].blurDp"),
          probe("quota-campo", "campo.y"),
          probe("inset-lista", "lista.y"),
        ),
      ))
      write(trace)
    }
  }

  @Test fun `esporta la selezione col medesimo campione di opacita e colore`() {
    val base = Color(0xFF26262A)
    val accent = Color(0xFF0A65CB)
    val frames = (0..24).map { index ->
      val t = glassTraceTimeMs(index, fps)
      val selected = FluidToolbarSelectionEasing.transform((t / GlassContentDefaults.DurationMs).toFloat().coerceAtMost(1f))
      GlassTraceFrame(t, listOf(
        selectedSlot("grassetto", "fluid:format_bold|bold", 25f, selected, base, accent),
        selectedSlot("corsivo", "fluid:format_italic|italic", 25f + FluidMetrics.GroupItemPitch.value, 1f - selected, base, accent),
      ))
    }
    write(GlassTrace("tinta-sincronizzata", "f4a-4", 3f, "light", 140f, 72f, frames, fps, head = paper))
  }

  @Test fun `esporta i puntini usando le misure di Note e Nota alla densita del tablet`() {
    val density = 2.125f
    val widthDp = 1600f / density
    val more = FluidIcons.MoreHoriz
    val share = FluidIcons.IosShare
    val metrics = fluidToolbarMetrics()
    val note = buildFluidToolbar(null) { button("trail", more, "Opzioni", onClick = {}) }
    val detail = buildFluidToolbar({}) {
      button("lead", share, "Condividi", onClick = {})
      spacer()
      button("trail", more, "Opzioni", onClick = {})
    }
    val before = resolveFluidToolbar(fluidToolbarModel(note, { 80f }), widthDp, metrics = metrics)
    val after = resolveFluidToolbar(fluidToolbarModel(detail, { 80f }), widthDp, metrics = metrics)
    val memory = FluidToolbarMemory()
    memory.update(before, FluidToolbarMotion.Full)
    memory.update(after, FluidToolbarMotion.Full)
    val transfer = memory.transfers.getValue("trail")
    val from = fluidToolbarIconCenterPx(before, before.element("trail")!!, density, rtl = false)
    val to = fluidToolbarIconCenterPx(after, after.element("trail")!!, density, rtl = false)
    assertEquals(from, to, 0f)
    val cropWidth = 76f
    val frames = (0..36).map { index ->
      val phase = index / 36f
      val center = transfer.center(from, to, to - 2f * phase * (1f - phase), lens, phase)
      assertEquals(to, center, 0f)
      GlassTraceFrame(glassTraceTimeMs(index, fps), listOf(mapOf(
        "name" to "puntini", "kind" to "shape", "x" to center / density - (widthDp - cropWidth) - lens / 2, "y" to 12,
        "w" to lens, "h" to lens, "radius" to "capsule", "color" to "#EFEEF4",
        "content" to listOf(mapOf("name" to "glifo", "icon" to "fluid:more_horiz|more", "iconSizeDp" to icon)),
      )))
    }
    write(GlassTrace("puntini-fermi", "f4a-4", density, "light", cropWidth, 66f, frames, fps,
      head = paper + mapOf("probes" to listOf(probe("centro-puntini", "puntini.x")))))
  }

  @Test fun `esporta le due geometrie della principale in movimento ridotto`() {
    val fade = TargetBasedAnimation(FluidMotion.crossFade<Float>(), Float.VectorConverter, 0f, 1f)
    val group = listOf("fluid:edit|pencil", "fluid:ios_share|share")
    val star = listOf("fluid:star|star")
    val frames = (0..18).map { index ->
      val opacity = fade.getValueFromNanos(glassTraceTimeNanos(index, fps)).coerceIn(0f, 1f)
      GlassTraceFrame(glassTraceTimeMs(index, fps), listOf(
        reducedGlass("prima", 37f, 3 * lens, 1f - opacity, group + star),
        reducedGlass("dopo-gruppo", 25f, 2 * lens, opacity, group),
        reducedGlass("dopo-principale", 25f + 2 * lens + FluidMetrics.GroupSpacing.value, lens, opacity, star, blue = true),
      ))
    }
    write(GlassTrace("principale-ridotta", "f4a-4", 3f, "light", 194f, 72f, frames, fps, head = paper))
  }

  private fun write(trace: GlassTrace) {
    GlassTraceOutput.requestedDir(System.getenv("GLASS_F4A4_DIR"))?.let {
      trace.write(File(it, "${trace.name}/trace.json"))
    }
  }

  private fun selectedSlot(name: String, icon: String, x: Float, selection: Float, base: Color, accent: Color) =
    mapOf("name" to name, "kind" to "shape", "x" to x, "y" to 14, "w" to lens, "h" to lens,
      "radius" to "capsule", "color" to glassColor(lerp(Color(0xFFF1F1F5), Color(0xFFC8DFFF), selection)),
      "content" to listOf(mapOf("name" to "glifo", "icon" to icon,
        "iconSizeDp" to this.icon, "color" to glassColor(lerp(base, accent, selection)))))

  private fun reducedGlass(name: String, x: Float, width: Float, alpha: Float, icons: List<String>, blue: Boolean = false) =
    mapOf("name" to name, "kind" to "shape", "x" to x, "y" to 14, "w" to width, "h" to lens,
      "radius" to "capsule", "color" to glassColor((if (blue) Color(0xFF1675EC) else Color(0xFFE4E4EA)).copy(alpha = alpha)),
      "content" to listOf(mapOf("name" to "icone", "icons" to icons, "iconSizeDp" to icon,
        "alpha" to alpha, "color" to if (blue) "#FFFFFF" else "#26262A")))

  private fun probe(name: String, path: String) = mapOf("name" to name, "kind" to "value", "path" to path)

  private fun frame(t: Double, progress: Float, reduced: Boolean): GlassTraceFrame {
    val look = fluidToolbarMinimizeLook(progress, travel, reduced, blurAvailable = true)
    val gap = (FluidScreenDefaults.TitleBottomSpacing + FluidScreenDefaults.ItemSpacing).value
    val titleLook = if (reduced) {
      val phase = fluidToolbarReducedFadeProgress(progress, travel * progress, gap)
      fluidToolbarMinimizeLook(phase, travel, reducedMotion = true, blurAvailable = true)
    } else look
    val blurDp = glassContentBlurRadiusPx(glassContentSigmaPx(look.blur, FluidMetrics.BarIconSize.value * 2f)) / 2f
    val fieldY = status + travel * (1f - progress)
    val glass = fluidToolbarSearchGlassProgress(progress)
    val elements = listOfNotNull(
      mapOf("name" to "lista", "kind" to "shape", "x" to 16, "y" to fieldY + searchHeight + 12f,
        "w" to 328, "h" to 60, "radius" to 15, "color" to "#EFEEF4", "content" to listOf(
          mapOf("name" to "riga", "label" to "Nota 1", "labelSizeDp" to 17))),
      mapOf("name" to "titolo", "kind" to "shape", "x" to 16, "y" to status, "w" to 250,
        "h" to 112, "color" to "#00000000", "content" to listOf(
          mapOf("name" to "testo", "x" to 0, "y" to 52f + titleLook.offsetPx, "w" to 108, "h" to 44,
            "label" to "Note", "labelSizeDp" to 34, "labelWeight" to 700,
            "alpha" to titleLook.alpha, "blurDp" to blurDp, "marginDp" to 20))),
      mapOf("name" to "tasti", "kind" to "shape", "x" to 300, "y" to status,
        "w" to lens, "h" to lens, "color" to "#00000000", "content" to listOf(
          mapOf("name" to "puntini", "y" to if (reduced) 0f else -progress * lens,
            "icon" to "fluid:more_horiz|more",
            "iconSizeDp" to icon, "alpha" to look.alpha, "blurDp" to blurDp, "marginDp" to 20))),
      mapOf("name" to "campo", "kind" to "shape",
        "x" to 16, "y" to fieldY, "w" to 328, "h" to searchHeight, "radius" to "capsule",
        "color" to glassColor(Color(0xFFE8E7EE).copy(alpha = 1f - glass))),
      if (glass > 0f) mapOf("name" to "vetro-campo", "kind" to "glass", "role" to "floating", "intensity" to glass,
        "x" to 16, "y" to fieldY, "w" to 328, "h" to searchHeight, "radius" to "capsule") else null,
      mapOf("name" to "testo-campo", "kind" to "shape",
        "x" to 16, "y" to fieldY, "w" to 328, "h" to searchHeight, "radius" to "capsule",
        "color" to "#00000000", "content" to listOf(
          mapOf("name" to "ricerca", "icon" to "fluid:search|search", "x" to 0, "w" to 48, "iconSizeDp" to 20),
          mapOf("name" to "prompt", "x" to 43, "w" to 150, "label" to "Cerca nelle note", "labelSizeDp" to 16))),
    )
    return GlassTraceFrame(t, elements)
  }
}
