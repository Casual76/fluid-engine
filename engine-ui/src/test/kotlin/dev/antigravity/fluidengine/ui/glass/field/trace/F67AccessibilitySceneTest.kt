package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassMaterialAccessibility
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import org.junit.Assert.assertTrue
import org.junit.Test

class F67AccessibilitySceneTest {
  @Test
  fun exportAccessibilityScenesWhenRequested() {
    val modes = mapOf(
      "serie" to GlassMaterialAccessibility.Default,
      "trasparenza-ridotta" to GlassMaterialAccessibility(reduceTransparency = true),
      "contrasto-medio" to GlassMaterialAccessibility(contrast = 0.5f),
      "contrasto-alto" to GlassMaterialAccessibility(contrast = 1f),
      "inattiva" to GlassMaterialAccessibility(receded = true),
      "insieme" to GlassMaterialAccessibility(reduceTransparency = true, contrast = 1f, receded = true),
    )
    val output = GlassSceneOutput.requestedDir(System.getenv("GLASS_F67_SCENE_DIR")) ?: return
    val scenes = modes.flatMap { (mode, access) ->
      listOf(false, true).map { dark -> scene(mode, access, dark) }
    }
    assertTrue(GlassSceneOutput.write(output, scenes).all { it.isFile && it.length() > 0L })
  }

  private fun scene(mode: String, access: GlassMaterialAccessibility, dark: Boolean): GlassScene {
    val theme = if (dark) "dark" else "light"
    val ink = if (dark) "#F2F2F5" else "#121214"

    fun slab(name: String, role: String, x: Int, y: Int, w: Int, h: Int, radius: Any = "capsule",
      variant: GlassLiquidVariant = GlassLiquidVariant.Regular, vararg content: Pair<String, Any>,
    ): Map<String, Any?> {
      val surface = sceneSurface(role)
      val body = glassRecipe(surface.role, w.toFloat(), h.toFloat(), dark, variant,
        optics = surface.optics, accessibility = access)
      val shape = linkedMapOf<String, Any?>(
        "name" to name, "x" to x, "y" to y, "w" to w, "h" to h, "radius" to radius, "contentColor" to ink,
      ).apply { putAll(content) }
      return liquidElement(shape, body, theme, "$role, $mode")
    }

    return GlassScene("$theme-$mode.json", linkedMapOf(
      "width" to 412, "height" to 448, "density" to 2, "theme" to theme, "corner" to "engine",
      "palette" to "engine",
      "background" to mapOf(
        "kind" to "checker", "stepDp" to 16, "color" to "#FFFFFF", "background" to "#000000",
      ),
      "elements" to listOf(
        slab("barra", "bar", 22, 22, 368, 58, content = arrayOf("label" to "Barra superiore")),
        slab("menu", "panel", 22, 104, 176, 178, 26,
          content = arrayOf(
            "rows" to listOf("Modifica", "Condividi", "Elimina").map { mapOf("label" to it) },
            "rowHeightDp" to 50,
          )),
        slab("foglio", "modal", 214, 104, 176, 214, 30,
          content = arrayOf(
            "rows" to listOf("Foglio", "Dettagli", "Conferma").map { mapOf("label" to it) },
            "rowHeightDp" to 60,
          )),
        slab("controllo", "button", 72, 303, 48, 48, variant = GlassLiquidVariant.Clear,
          content = arrayOf("icon" to "plus")),
        slab("tabbar", "floating", 22, 365, 368, 63,
          content = arrayOf("icons" to listOf("home", "search", "plus"))),
      ),
    ))
  }
}
