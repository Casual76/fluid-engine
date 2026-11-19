package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.fluidProminentCoverage
import dev.antigravity.fluidengine.ui.fluid.fluidProminentInk
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Le scene di F6.4 per il banco, dalla ricetta: la tavola 44 contro 46 dp con le icone a 0,6 del vetro (in una barra e in
 * un gruppo, come i ritagli di Apple di R6), le tre taglie prima e dopo (icone a 0,5 e a 0,6 del vetro) nei due temi, e
 * il tasto tinto di F2 contro lo stile `Prominent` sopra le due foto della chiusura di F2.
 *
 * Si scrivono solo con `GLASS_F64_SCENE_DIR` (come le scene di F6.7): i giri normali compongono e controllano le scene
 * senza toccare il repo.
 */
class F64ButtonSceneTest {

  private val inkLight = "#121214"
  private val inkDark = "#F2F2F5"

  @Test
  fun exportButtonScenesWhenRequested() {
    val scenes = barScenes() + sizeScenes() + prominentScenes()
    assertEquals(10, scenes.size)
    // Ogni lastra viene dalla ricetta: nessuna scena e' vuota.
    scenes.forEach { scene ->
      @Suppress("UNCHECKED_CAST")
      val elements = scene.json["elements"] as List<Map<String, Any?>>
      assertTrue(scene.path, elements.isNotEmpty() && elements.all { it["kind"] == "material" })
    }
    val output = GlassSceneOutput.requestedDir(System.getenv("GLASS_F64_SCENE_DIR")) ?: return
    assertTrue(GlassSceneOutput.write(output, scenes).all { it.isFile && it.length() > 0L })
  }

  /** Una lastra di un tasto (`button`: ruolo interattivo, ottica dei tasti) dalla ricetta, col suo contenuto. */
  private fun slab(
    name: String, x: Float, y: Float, w: Float, h: Float, dark: Boolean,
    vararg content: Pair<String, Any?>,
    variant: GlassLiquidVariant = GlassLiquidVariant.Regular,
    film: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    role: String = "button",
    note: String = "",
  ): Map<String, Any?> {
    val surface = sceneSurface(role)
    val recipe = glassRecipe(surface.role, w, h, dark, variant, surface.optics, film = film, tint = tint)
    val shape = linkedMapOf<String, Any?>("name" to name, "x" to x, "y" to y, "w" to w, "h" to h, "radius" to "capsule")
    shape.putAll(content)
    val side = if (dark) "scuro" else "chiaro"
    return liquidElement(shape, recipe, null, "glassRecipe: ruolo $role, $w x $h dp, lato $side$note")
  }

  private fun dp(value: Float): Float = (value * 100f).roundToInt() / 100f

  /**
   * La barra in alto di R6 (iOS 27, 269 f11400: indietro, un tondo, un gruppo di tre, «altro») e i gruppi in basso di
   * iOS 26 (219 f28855), col vetro del tasto a [lens] dp e le icone a 0,6 del vetro. Il passo nel gruppo e' il vetro
   * (`GroupItemPitch` vale 1,0 volte l'altezza), i margini quelli di `FluidMetrics`.
   */
  private fun barScenes(): List<GlassScene> = listOf(44f, 46f).flatMap { lens ->
    listOf(false, true).map { dark ->
      val icon = FluidMetrics.iconSizeIn(androidx.compose.ui.unit.Dp(lens)).value
      val back = dp(FluidMetrics.BackIconSize.value * lens / 44f)
      val ink = if (dark) inkDark else inkLight
      val width = 412f
      val edge = FluidMetrics.ScreenEdgeMargin.value
      val group = FluidMetrics.GroupSpacing.value
      val more = width - edge - lens
      val capsule = more - group - 3f * lens
      val single = capsule - group - lens
      val top = 18f
      val bottom = 104f
      val theme = if (dark) "dark" else "light"
      GlassScene("tavola-44-46/barra-${lens.roundToInt()}-$theme.json", linkedMapOf(
        "_" to "F6.4, la scelta 44 / 46 dp: vetro del tasto a ${lens.roundToInt()} dp, icone a ${icon.roundToInt()} dp " +
          "(0,6 del vetro). In alto la barra di R6 (iOS 27) su una foto, in basso i gruppi di iOS 26. Dalla ricetta " +
          "(F64ButtonSceneTest): non si modifica a mano.",
        "width" to width, "height" to 180, "density" to 3, "theme" to theme, "palette" to "engine", "corner" to "engine",
        "background" to if (dark) {
          linkedMapOf("kind" to "gradient4", "tl" to "#1C1C1E", "tr" to "#2A2A2E", "bl" to "#101012", "br" to "#232326")
        } else {
          linkedMapOf("kind" to "gradient4", "tl" to "#D9DADF", "tr" to "#C9CBD1", "bl" to "#BFC2C9", "br" to "#9EA2AB")
        },
        "page" to listOf(
          // La foto dietro la barra in alto (la moquette del ritaglio di Apple: un mezzo tono caldo e granuloso).
          linkedMapOf(
            "kind" to "background", "x" to 0, "y" to 0, "w" to width, "h" to 84,
            "background" to linkedMapOf(
              "kind" to "synthetic", "palette" to listOf("#3A332C", "#5C5248", "#7D7266", "#4A4038", "#968A7C"),
              "scaleDp" to 7, "detail" to 1.0, "contrast" to 0.35,
            ),
          ),
        ),
        "elements" to listOf(
          slab("indietro", edge, top, lens, lens, dark, "icon" to "fluid:arrow_back_ios_new|back", "iconSizeDp" to back,
            "contentColor" to ink),
          slab("tondo", single, top, lens, lens, dark, "icon" to "fluid:folder|plus", "iconSizeDp" to icon, "contentColor" to ink),
          slab("gruppo", capsule, top, 3f * lens, lens, dark,
            "icons" to listOf("fluid:image|plus", "fluid:download|plus", "fluid:edit|plus"), "iconSizeDp" to icon,
            "contentColor" to ink),
          slab("altro", more, top, lens, lens, dark, "icon" to "fluid:more_horiz|plus", "iconSizeDp" to icon, "contentColor" to ink),
          slab("gruppo-sinistra", edge, bottom, 2f * lens, lens, dark,
            "icons" to listOf("fluid:folder|plus", "fluid:delete|close"), "iconSizeDp" to icon, "contentColor" to ink),
          slab("gruppo-destra", width - edge - 2f * lens, bottom, 2f * lens, lens, dark,
            "icons" to listOf("fluid:ios_share|share", "fluid:send|plus"), "iconSizeDp" to icon, "contentColor" to ink),
        ),
      ))
    }
  }

  /**
   * Le tre taglie, nei due temi: in alto le icone a meta' del vetro (la 3.x: 20, 22, 27 dp), in basso a 0,6 (24, 26,
   * 32 dp), un tondo e un tasto con etichetta per taglia. Sul fondo della pagina del tema.
   */
  private fun sizeScenes(): List<GlassScene> = listOf(false, true).map { dark ->
    val ink = if (dark) inkDark else inkLight
    val theme = if (dark) "dark" else "light"
    val elements = ArrayList<Map<String, Any?>>()
    listOf("prima" to 0.5f, "dopo" to FluidMetrics.IconLensRatio).forEachIndexed { row, (name, ratio) ->
      var x = 16f
      val y = 20f + row * 84f
      FluidGlassSize.entries.forEach { size ->
        val lens = size.lens.value
        val icon = (lens * ratio).roundToInt().toFloat()
        elements += slab("$name-${size.name}", x, y + (54f - lens) / 2f, lens, lens, dark,
          "icon" to "fluid:share|share", "iconSizeDp" to icon, "contentColor" to ink)
        x += lens + 16f
      }
    }
    GlassScene("taglie/taglie-$theme.json", linkedMapOf(
      "_" to "F6.4, le icone dei tasti per taglia: in alto a 0,5 del vetro (3.x), in basso a 0,6 (4.0, R6). Compact 40, " +
        "Regular 44, Large 54 dp. Dalla ricetta (F64ButtonSceneTest).",
      "width" to 220, "height" to 188, "density" to 3, "theme" to theme, "palette" to "engine", "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to if (dark) "#000000" else "#F2F2F7"),
      "elements" to elements,
    ))
  }

  /**
   * Il debito di F2: il tasto tinto scritto a mano (accento al 50%, tinta di Apple, etichetta del tema, lastra
   * `Floating` 120 x 48 dp come nella galleria) sopra la foto scura in tema chiaro e sopra la foto chiara in tema scuro,
   * accanto allo stile `Prominent` (la stessa tinta alla copertura che regge l'etichetta, dipinta com'e', l'inchiostro
   * della tinta) e al vetro di serie.
   */
  private fun prominentScenes(): List<GlassScene> = listOf(false, true).flatMap { dark ->
    val accent = FluidDefaultBrand.resolve(dark)
    val coverage = fluidProminentCoverage(accent)
    val ink = glassColor(fluidProminentInk(accent)).substring(0, 7)
    val themeInk = if (dark) inkDark else inkLight
    val theme = if (dark) "dark" else "light"
    val photos = listOf(
      "scura" to listOf("#14202E", "#1E2C3C", "#2B3B4E", "#3A2E22", "#0E151E"),
      "chiara" to listOf("#E9E2D4", "#F4EFE6", "#D7CDBB", "#EFE6D2", "#FBF8F2"),
    )
    photos.map { (photo, palette) ->
      GlassScene("prominent/prominent-$theme-foto-$photo.json", linkedMapOf(
        "_" to "F6.4, l'etichetta del tasto tinto sopra una foto ($photo, tema $theme). A sinistra la tinta scritta a mano " +
          "di F2 (accento al 50%, Colored, etichetta del tema); al centro `Prominent` (copertura ${"%.3f".format(coverage)}, " +
          "Fixed, inchiostro $ink); a destra il vetro di serie. Dalla ricetta (F64ButtonSceneTest).",
        "width" to 412, "height" to 96, "density" to 3, "theme" to theme, "palette" to "engine", "corner" to "engine",
        "background" to linkedMapOf("kind" to "synthetic", "palette" to palette, "scaleDp" to 40, "detail" to 0.6,
          "contrast" to 0.7),
        "elements" to listOf(
          slab("tinto-a-mano", 12f, 24f, 120f, 48f, dark,
            "label" to "Tinto", "labelSizeDp" to 14, "labelWeight" to 500, "contentColor" to themeInk,
            tint = accent.copy(alpha = 0.5f), role = "floating", note = ", tinta di Apple accento al 50% (F2)"),
          slab("prominent", 146f, 24f, 120f, 48f, dark,
            "label" to "Fatto", "labelSizeDp" to 17, "labelWeight" to 600, "contentColor" to ink,
            film = accent.copy(alpha = coverage), note = ", Prominent: pellicola accento a ${"%.3f".format(coverage)}"),
          slab("vetro", 280f, 24f, 120f, 48f, dark, "label" to "Annulla", "labelSizeDp" to 17, "labelWeight" to 600,
            "contentColor" to themeInk),
        ),
      ))
    }
  }
}
