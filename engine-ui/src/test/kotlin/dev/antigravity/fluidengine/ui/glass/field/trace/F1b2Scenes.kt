package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidFormPresets
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildSlabPlan
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp

/*
 * F1B.2: le scene della parità dell'ombra e le tracce dei quattro viaggi d'oro col materiale unificato.
 *
 *  - `ombra-riposo-viaggio-{testo,menu}-{riposo,viaggio}-{chiaro,scuro}` (`GLASS_F1B2_SCENE_DIR`, di solito
 *    `tools/glassbench/scenes/f1b`): le stesse lastre rese due volte, come le lascia una superficie ferma (la lastra di
 *    `glassSurface`, ombra a **maschere**) e come le porta l'ultimo fotogramma di un viaggio che arriva a loro (la
 *    superficie fisica, ombra **nel corpo**). La differenza si legge con `compare`: è lo scalino al passaggio di
 *    consegne. Sopra un testo (tasto, capsula e due tasti a 8 dp, come una barra) e sotto un menu (il pannello e un
 *    tasto, sopra righe di contenuto), nei due temi;
 *  - le quattro tracce (`GLASS_TRACE_DIR`, fase `f1b-2`), in chiaro e in scuro: stessa corsa di F1B.1, più l'ombra
 *    unica, la goccia con gli angoli della lastra, e nella barra il passaggio di consegne con l'ordine vero.
 */
internal object F1b2Scenes {

  const val Density: Float = 3f

  private class Theme(val name: String, val suffix: String, val dark: Boolean, val page: String, val ink: String, val row: String)

  private val Themes = listOf(
    Theme("light", "chiaro", dark = false, page = "#F2F2F7", ink = "#0A0A0A", row = "#FFFFFF"),
    Theme("dark", "scuro", dark = true, page = "#1C1C1E", ink = "#F2F2F7", row = "#2C2C2E"),
  )

  private const val Lorem = "Signs are made using neon lights. Light is used to define color, tone, and shape. They are " +
    "irreplaceable, and depend on the people who bend them all over the world. Glass is bent by hand, one letter at a time."

  /** Quale dei due modi di disegnare la stessa lastra. */
  enum class Way(val suffix: String) {
    /** La lastra ferma di `glassSurface`: ombra a maschere, luce sul nodo. È il metro. */
    Rest("riposo"),

    /** L'ultimo fotogramma di un viaggio che arriva a lei: superficie fisica, ombra e luce nel corpo. */
    Travel("viaggio"),
  }

  /** Un pezzo della scena: dove sta, che ruolo e che ottica ha. */
  private class Piece(val name: String, val form: FluidForm.Slab, val role: GlassRole, val optics: GlassOptics, val radius: Any)

  private fun px(dp: Float) = dp * Density

  private fun circle(name: String, leftDp: Float, topDp: Float) = Piece(
    name, FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + 44f), px(topDp + 44f))),
    GlassRole.Interactive, GlassDefaults.buttonOptics(), "capsule",
  )

  private fun capsule(name: String, leftDp: Float, topDp: Float, wDp: Float, hDp: Float) = Piece(
    name, FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + wDp), px(topDp + hDp))),
    GlassRole.Floating, GlassDefaults.optics(GlassRole.Floating), "capsule",
  )

  private fun menu(name: String, leftDp: Float, topDp: Float) = Piece(
    name,
    FluidForm.Slab(
      frame = Rect(px(leftDp), px(topDp), px(leftDp + 170f), px(topDp + 192f)),
      cornerRadii = FluidCornerRadii.all(px(28f)),
      smoothing = ContinuousCornerShape.IosSmoothing,
    ),
    GlassRole.Modal, GlassDefaults.panelOptics(compact = true), 28f,
  )

  /** Da dove arriva il viaggio che si posa sul pezzo: una forma diversa, perché il viaggio sia vero. */
  private fun elsewhere(slab: FluidForm.Slab): FluidForm.Slab {
    val f = slab.frame
    return FluidForm.Slab(
      frame = Rect(f.left - px(20f), f.top - px(10f), f.right + px(40f), f.bottom + px(30f)),
      cornerRadii = FluidCornerRadii.all(px(12f)),
      smoothing = ContinuousCornerShape.IosSmoothing,
    )
  }

  /** La lastra di `glassSurface`, con la sua ombra a maschere e la cattura a risoluzione piena (come la fisica). */
  private fun rest(piece: Piece, dark: Boolean): Map<String, Any?> {
    val f = piece.form.frame
    val recipe = glassRecipe(
      role = piece.role,
      widthDp = f.width / Density,
      heightDp = f.height / Density,
      darkSide = dark,
      optics = piece.optics.sanitized(),
    )
    val shape = linkedMapOf<String, Any?>(
      "name" to piece.name, "x" to f.left / Density, "y" to f.top / Density, "w" to f.width / Density,
      "h" to f.height / Density, "radius" to piece.radius,
    )
    val element = liquidElement(shape, recipe, theme = null, note = "glassSurface, ${piece.role.name}, ombra a maschere")
    @Suppress("UNCHECKED_CAST")
    (element["capture"] as MutableMap<String, Any?>)["scale"] = 1
    return element
  }

  /**
   * La superficie fisica all'ultimo fotogramma di un viaggio che arriva a [pieces] (lontani fra loro: il collo è
   * spento), con l'ombra nel corpo. Il palco è grande quanto la tela, come i palchi dell'engine che non allargano il
   * nodo (menu, modale, Playground): l'ombra non è tagliata.
   */
  private fun travel(name: String, pieces: List<Piece>, dark: Boolean, canvasWidthDp: Float, canvasHeightDp: Float): Map<String, Any?> {
    val first = pieces.first()
    val plan = PhysicsRenderPlan().also {
      buildSlabPlan(it, pieces.map { p -> elsewhere(p.form) to p.form }, px(16f), t = 1f, fusion = 0f)
    }
    val dress = LiquidPhysicsDress(first.role, PhysicsMaterialSource(null, first.optics.sanitized()), Density, darkSide = dark, shadow = true)
    val recipe = dress.recipe(plan, opticsBlend = 1f, motion = 0f)
    val inputs = liquidPhysicsInputs(plan, recipe, Density, dress.paddingDp, rest = false, shadow = true)
    return liquidPhysicsElement(
      name = name,
      inputs = inputs,
      origin = Offset.Zero,
      density = Density,
      canvasWidthDp = canvasWidthDp,
      canvasHeightDp = canvasHeightDp,
      note = "Fluid-physics, ${first.role.name}, ultimo fotogramma del viaggio, ${plan.pieceCount} pezzi, ombra nel corpo",
      still = true,
    )
  }

  private fun scene(
    path: String,
    note: String,
    theme: Theme,
    width: Int,
    height: Int,
    page: List<Map<String, Any?>>,
    elements: List<Map<String, Any?>>,
  ): GlassScene {
    val json = LinkedHashMap<String, Any?>()
    json["_"] = "Scritta dall'engine (F1b2ExportTest, F1B.2): $note Non si modifica a mano: si rigenera."
    json["width"] = width
    json["height"] = height
    json["density"] = Density
    json["theme"] = theme.name
    json["palette"] = "engine"
    json["corner"] = "engine"
    json["background"] = linkedMapOf(
      "kind" to "text", "background" to theme.page, "color" to theme.ink, "fontSizeDp" to 20, "lineHeightDp" to 27,
      "paddingDp" to 10, "text" to "$Lorem $Lorem $Lorem $Lorem",
    )
    json["page"] = page
    json["elements"] = elements
    return GlassScene("$path-${theme.suffix}.json", json)
  }

  /** Sopra un testo: un tasto, una capsula, e due tasti a 8 dp l'uno dall'altro (i pezzi di una barra). */
  private fun onText(way: Way, theme: Theme): GlassScene {
    val tasto = circle("tasto", 40f, 60f)
    val capsula = capsule("capsula", 140f, 54f, 156f, 56f)
    val pair = listOf(circle("coppia-a", 330f, 60f), circle("coppia-b", 382f, 60f))
    val width = 480
    val height = 220
    val elements = when (way) {
      Way.Rest -> (listOf(tasto, capsula) + pair).map { rest(it, theme.dark) }
      Way.Travel -> listOf(
        travel("tasto", listOf(tasto), theme.dark, width.toFloat(), height.toFloat()),
        travel("capsula", listOf(capsula), theme.dark, width.toFloat(), height.toFloat()),
        travel("coppia", pair, theme.dark, width.toFloat(), height.toFloat()),
      )
    }
    return scene(
      path = "ombra-riposo-viaggio-testo-${way.suffix}",
      note = "l'ombra unica sopra un testo: tasto 44 dp, capsula 156 x 56, due tasti a 8 dp. «${way.suffix}»: " +
        if (way == Way.Rest) "lastre di `glassSurface`, ombra a maschere." else "superficie fisica all'arrivo, ombra nel corpo.",
      theme = theme,
      width = width,
      height = height,
      page = emptyList(),
      elements = elements,
    )
  }

  /** Sotto un menu: il pannello 170 x 192 e un tasto, sopra righe di contenuto. */
  private fun underMenu(way: Way, theme: Theme): GlassScene {
    val pannello = menu("menu", 150f, 30f)
    val tasto = circle("tasto", 50f, 140f)
    val width = 480
    val height = 330
    val rows = (0 until 6).map { i ->
      linkedMapOf<String, Any?>(
        "kind" to "rect", "x" to 16, "y" to 20 + i * 50, "w" to 448, "h" to 40, "color" to theme.row,
      )
    }
    val elements = when (way) {
      Way.Rest -> listOf(pannello, tasto).map { rest(it, theme.dark) }
      Way.Travel -> listOf(
        travel("menu", listOf(pannello), theme.dark, width.toFloat(), height.toFloat()),
        travel("tasto", listOf(tasto), theme.dark, width.toFloat(), height.toFloat()),
      )
    }
    return scene(
      path = "ombra-riposo-viaggio-menu-${way.suffix}",
      note = "l'ombra unica sotto un menu 170 x 192 dp e un tasto, sopra righe di contenuto. «${way.suffix}»: " +
        if (way == Way.Rest) "lastre di `glassSurface`, ombra a maschere." else "superficie fisica all'arrivo, ombra nel corpo.",
      theme = theme,
      width = width,
      height = height,
      page = rows,
      elements = elements,
    )
  }

  fun all(): List<GlassScene> = Themes.flatMap { theme ->
    Way.entries.flatMap { way -> listOf(onText(way, theme), underMenu(way, theme)) }
  }
}

/** Le quattro tracce dei viaggi d'oro di F1B.2, in chiaro e in scuro (fase `f1b-2`). */
internal object F1b2Traces {
  const val Phase = "f1b-2"

  private fun suffix(dark: Boolean) = if (dark) "-scuro" else ""

  fun menu(dark: Boolean): MenuBuilt = buildMenuTrace(
    scene = OggiMenuApertura.scene(),
    dress = TraceDress.Liquid,
    name = F1bTraces.MenuName + suffix(dark),
    phase = Phase,
    dark = dark,
    unified = true,
    background = if (dark) linkedMapOf("kind" to "color", "color" to "#1C1C1E") else null,
  )

  /**
   * La barra: la scena di F0.5 su una tela più alta (la riga più in basso, perché l'ombra dei tasti ci stia: la
   * geometria del viaggio non cambia), col nodo della superficie allargato come in `FluidAnchoredBar`.
   */
  fun bar(dark: Boolean): BarBuilt {
    val base = OggiBarraPagine.scene()
    val scene = BarScene(
      name = base.name,
      fps = base.fps,
      density = base.density,
      widthDp = base.widthDp,
      rowTopDp = 70f,
      canvasHeightDp = 184f,
      lower = base.lower,
      upperActions = base.upperActions,
      upperBack = base.upperBack,
      preambleFrames = base.preambleFrames,
      tailFrames = 6,
      maxFrames = base.maxFrames,
      head = base.head,
    )
    return buildBarTrace(
      scene = scene,
      dress = TraceDress.Liquid,
      name = F1bTraces.BarName + suffix(dark),
      phase = Phase,
      dark = dark,
      shadow = true,
      handoff = true,
      stageGrowDp = glassTravelShadowReachDp(GlassDefaults.buttonOptics().sanitized(), gain = 0f),
      background = if (dark) {
        linkedMapOf("kind" to "gradient4", "tl" to "#3A3D44", "tr" to "#1E2026", "bl" to "#36353A", "br" to "#222428")
      } else {
        null
      },
    )
  }

  fun split(dark: Boolean): JourneyBuilt =
    F1bTraces.split(dark = dark, shadow = true, name = F1bTraces.SplitName + suffix(dark), phase = Phase)

  fun groupToMenu(dark: Boolean): JourneyBuilt =
    F1bTraces.groupToMenu(dark = dark, shadow = true, name = F1bTraces.GroupMenuName + suffix(dark), phase = Phase)
}
