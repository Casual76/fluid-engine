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
import dev.antigravity.fluidengine.ui.fluidphysics.buildRestPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildSlabPlan
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe

/*
 * Le scene ferme di F1B.1 (`tools/glassbench/scenes/f1b/`), scritte dal codice dell'engine: il piano lo costruisce
 * il motore (`buildRestPlan`, `buildSlabPlan`), la ricetta è `physicsLiquidRecipe`, gli ingressi sono
 * `physicsLiquidInputs`. Niente qui dentro decide un numero del materiale.
 *
 * Ogni scena ha **tre fasce**, una sotto l'altra, con le stesse lastre: la pagina piatta dell'engine, un testo, una
 * foto (sintetica). E c'è nei due temi (`-chiaro`, `-scuro`). I piani si costruiscono a densità 3 e si scrivono in
 * dp; la regola forma → piano deve dare lo stesso esito a 2,125 (lo controlla `F1bSceneExportTest`).
 *
 * L'ombra non c'è in nessuna (vedi `LiquidPhysics.kt`): è di F1B.2.
 */
internal object F1bScenes {

  /** La densità a cui si costruiscono i piani. */
  const val Density: Float = 3f

  private const val Lorem = "Signs are made using neon lights. Light is used to define color, tone, and shape. " +
    "They are irreplaceable, and depend on the people who bend them all over the world. Glass is bent by hand."

  private class Theme(val name: String, val suffix: String, val dark: Boolean, val page: String, val ink: String)

  private val Themes = listOf(
    Theme("light", "chiaro", dark = false, page = "#F2F2F7", ink = "#0A0A0A"),
    Theme("dark", "scuro", dark = true, page = "#0D0D0F", ink = "#F2F2F7"),
  )

  /** Le tre fasce: pagina piatta, testo, foto. */
  private fun bands(theme: Theme, width: Int, bandHeight: Int): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "kind" to "background", "x" to 0, "y" to bandHeight, "w" to width, "h" to bandHeight,
      "background" to linkedMapOf(
        "kind" to "text", "background" to theme.page, "color" to theme.ink, "fontSizeDp" to 22, "lineHeightDp" to 30,
        "paddingDp" to 12, "text" to "$Lorem $Lorem",
      ),
    ),
    linkedMapOf(
      "kind" to "background", "x" to 0, "y" to bandHeight * 2, "w" to width, "h" to bandHeight,
      "background" to if (theme.dark) {
        linkedMapOf("kind" to "synthetic", "scaleDp" to 80, "detail" to 0.6, "contrast" to 0.9, "lift" to -0.12)
      } else {
        linkedMapOf("kind" to "synthetic", "scaleDp" to 80, "detail" to 0.6, "contrast" to 0.9)
      },
    ),
  )

  private fun scene(
    path: String,
    note: String,
    theme: Theme,
    width: Int,
    bandHeight: Int,
    perBand: (bandTopDp: Float, band: Int) -> List<Map<String, Any?>>,
  ): GlassScene {
    val json = LinkedHashMap<String, Any?>()
    json["_"] = "Scritta dall'engine (F1bSceneExportTest, F1B.1): $note Tre fasce con le stesse lastre: pagina " +
      "piatta, testo, foto. Senza ombra (F1B.2). Non si modifica a mano: si rigenera."
    json["width"] = width
    json["height"] = bandHeight * 3
    json["density"] = Density
    json["theme"] = theme.name
    json["palette"] = "engine"
    json["corner"] = "engine"
    json["background"] = linkedMapOf("kind" to "color", "color" to theme.page)
    json["page"] = bands(theme, width, bandHeight)
    json["elements"] = (0 until 3).flatMap { band -> perBand(bandHeight * band.toFloat(), band) }
    return GlassScene("$path-${theme.suffix}.json", json)
  }

  private fun px(dp: Float) = dp * Density

  private fun circle(leftDp: Float, topDp: Float, sizeDp: Float): FluidForm.Slab =
    FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + sizeDp), px(topDp + sizeDp)))

  private fun capsule(leftDp: Float, topDp: Float, wDp: Float, hDp: Float): FluidForm.Slab =
    FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + wDp), px(topDp + hDp)))

  private fun panel(leftDp: Float, topDp: Float, wDp: Float, hDp: Float, radiusDp: Float): FluidForm.Slab = FluidForm.Slab(
    frame = Rect(px(leftDp), px(topDp), px(leftDp + wDp), px(topDp + hDp)),
    cornerRadii = FluidCornerRadii.all(px(radiusDp)),
    smoothing = ContinuousCornerShape.IosSmoothing,
  )

  private fun inflate(rect: Rect, dp: Float): Rect =
    Rect(rect.left - px(dp), rect.top - px(dp), rect.right + px(dp), rect.bottom + px(dp))

  /**
   * Una superficie fisica ferma o in viaggio, come elemento: il piano [plan] con la ricetta di [role] e [optics].
   *
   * @param rest vero = un piano di riposo, falso = uno di viaggio: finisce nella nota della scena. Il disegno è lo
   *   stesso programma nei due casi (luce in corpo, ritaglio del nodo emulato).
   */
  private fun physics(
    name: String,
    plan: PhysicsRenderPlan,
    role: GlassRole,
    optics: GlassOptics,
    dark: Boolean,
    rest: Boolean,
    stageMarginDp: Float = 24f,
    /** F1B.2: l'ombra nel corpo (la tabella riposo ↔ viaggio rifatta con l'ombra accesa). */
    shadow: Boolean = false,
  ): Map<String, Any?> {
    val dress = LiquidPhysicsDress(role, PhysicsMaterialSource(null, optics.sanitized()), Density, darkSide = dark, shadow = shadow)
    val recipe = dress.recipe(plan, opticsBlend = 1f, motion = 0f)
    val inputs = liquidPhysicsInputs(plan, recipe, Density, dress.paddingDp, rest, shadow)
    return liquidPhysicsElement(
      name = name,
      inputs = inputs,
      origin = Offset.Zero,
      density = Density,
      canvasWidthDp = 0f,
      canvasHeightDp = 0f,
      stage = inflate(plan.bounds, stageMarginDp),
      note = "Fluid-physics, ${role.name}, ${if (rest) "fermo" else "in viaggio"}, ${plan.pieceCount} pezzi, fusione ${plan.fusion}",
      still = true,
    )
  }

  private fun restPlan(form: FluidForm): PhysicsRenderPlan = PhysicsRenderPlan().also { buildRestPlan(it, form) }

  /** Un gruppo a metà viaggio: i pezzi dove stanno, col collo vivo (`fusion` 1). */
  private fun travelPlan(slabs: List<FluidForm.Slab>, blendDp: Float): PhysicsRenderPlan =
    PhysicsRenderPlan().also { buildSlabPlan(it, slabs.map { s -> s to s }, px(blendDp), t = 0.5f, fusion = 1f) }

  private val Button = GlassDefaults.buttonOptics()

  // ---- gruppo-2, gruppo-4, gruppo-8 --------------------------------------------------------------------------

  private fun group(count: Int, theme: Theme): GlassScene = scene(
    path = "gruppo-$count",
    note = "un gruppo di $count tasti da 44 dp in un campo solo (`GlassBody8`). Sopra a riposo, a 8 dp l'uno " +
      "dall'altro: pezzi separati del tutto. Sotto a metà di un viaggio, a 4 dp e col " +
      "collo vivo (fusione 16 dp): un campo e un filo soli. La luce è nel corpo in tutte e due le righe.",
    theme = theme,
    width = 480,
    bandHeight = 180,
  ) { top, band ->
    val total = count * 44f + (count - 1) * 8f
    val left = (480f - total) / 2f
    val rest = List(count) { i -> circle(left + i * 52f, top + 28f, 44f) }
    val totalNear = count * 44f + (count - 1) * 4f
    val leftNear = (480f - totalNear) / 2f
    val near = List(count) { i -> circle(leftNear + i * 48f, top + 108f, 44f) }
    listOf(
      physics("riposo-$band", restPlan(FluidForm.Group(rest, blendRadius = px(16f))), GlassRole.Interactive, Button, theme.dark, rest = true),
      physics("viaggio-$band", travelPlan(near, 16f), GlassRole.Interactive, Button, theme.dark, rest = false),
    )
  }

  // ---- ponte-stretto ---------------------------------------------------------------------------------------------

  val BridgeGapsDp: List<Float> = listOf(0f, 2f, 6f, 12f, 20f)

  /**
   * I progressi a cui la riga «nascita» fotografa il viaggio di un tasto che **nasce dal fianco del vicino**: da
   * taglia zero, attaccato, fino a 44 dp staccato di [BirthGapDp]. Il primo è la nascita, l'ultimo l'arrivo.
   */
  val BirthProgress: List<Float> = listOf(0f, 0.15f, 0.35f, 0.6f, 1f)
  const val BirthGapDp: Float = 8f

  /** Di quanto il seme di [birthPlan] sta **dentro** il fianco del vicino. */
  const val BirthSeedInsetDp: Float = 1f

  /**
   * Il piano del viaggio «un tasto nasce dal fianco del vicino» a progresso [t], costruito dal motore
   * (`buildSlabPlan`, col collo di serie di `travelFusion`): il vicino sta fermo, il nuovo parte da un punto appena
   * dentro il suo fianco destro e arriva a un tasto da 44 dp a [BirthGapDp] di distanza.
   *
   * **Regola per chi costruirà questo viaggio** (fasi dopo): il seme nasce *dentro* il vicino, non sul suo bordo.
   * Il motore non disegna pezzi più piccoli di un pixel (`MinSlabSidePx`), e un seme posato esattamente sul bordo
   * sporge di quel pixel al primo fotogramma (visto sul banco, `bench/f1b-1/nascita-0-fianco.png`). Si controlla
   * così: al progresso 0 il seme sta tutto dentro il vicino (`F1bExportTest`, sul piano) e la resa del primo
   * fotogramma è quella del vicino da solo.
   */
  fun birthPlan(leftDp: Float, topDp: Float, t: Float): PhysicsRenderPlan {
    val neighbour = circle(leftDp, topDp, 44f)
    val seedX = px(leftDp + 44f - BirthSeedInsetDp)
    val flank = Rect(seedX, px(topDp + 22f), seedX, px(topDp + 22f))
    val seed = FluidForm.Slab(frame = flank, cornerRadii = FluidCornerRadii.all(0f), smoothing = 0f)
    val grown = circle(leftDp + 44f + BirthGapDp, topDp, 44f)
    return PhysicsRenderPlan().also { buildSlabPlan(it, listOf(neighbour to neighbour, seed to grown), px(16f), t) }
  }

  private fun bridge(theme: Theme): GlassScene = scene(
    path = "ponte-stretto",
    note = "due tasti da 44 dp a ${BridgeGapsDp.joinToString { it.toInt().toString() }} dp l'uno dall'altro, fusione 16 dp. " +
      "Sopra in viaggio (collo vivo): il collo nasce e muore col varco. In mezzo le stesse coppie a riposo: separate. " +
      "Sotto un tasto che nasce dal fianco del vicino (da taglia zero, attaccato) e se ne stacca, ai progressi " +
      "${BirthProgress.joinToString()} del viaggio del motore: il collo nasce col pezzo e si rompe prima dell'arrivo.",
    theme = theme,
    width = 640,
    bandHeight = 264,
  ) { top, band ->
    var x = 16f
    val out = ArrayList<Map<String, Any?>>()
    BridgeGapsDp.forEachIndexed { i, gap ->
      val pairTop = listOf(circle(x, top + 24f, 44f), circle(x + 44f + gap, top + 24f, 44f))
      val pairBottom = listOf(circle(x, top + 108f, 44f), circle(x + 44f + gap, top + 108f, 44f))
      out += physics("viaggio-$band-$i", travelPlan(pairTop, 16f), GlassRole.Interactive, Button, theme.dark, rest = false, stageMarginDp = 7f)
      out += physics(
        "riposo-$band-$i", restPlan(FluidForm.Group(pairBottom, blendRadius = px(16f))), GlassRole.Interactive, Button, theme.dark,
        rest = true, stageMarginDp = 7f,
      )
      out += physics(
        "nascita-$band-$i", birthPlan(x, top + 192f, BirthProgress[i]), GlassRole.Interactive, Button, theme.dark,
        rest = false, stageMarginDp = 7f,
      )
      x += 88f + gap + 28f
    }
    out
  }

  // ---- riposo-contro-viaggio ---------------------------------------------------------------------------------

  /** Le tre lastre del confronto: come si chiamano, il ruolo, l'ottica, e la forma a [top] dp. */
  private class Slab(val name: String, val benchRole: String, val form: (top: Float) -> FluidForm.Slab, val radius: Any)

  private val Slabs = listOf(
    Slab("tasto", "button", { top -> circle(30f, top + 93f, 44f) }, "capsule"),
    Slab("capsula", "floating", { top -> capsule(104f, top + 87f, 156f, 56f) }, "capsule"),
    Slab("menu", "compactpanel", { top -> panel(290f, top + 19f, 170f, 192f, 28f) }, 28),
  )

  /** Verso cosa viaggia (o da cosa arriva) ogni lastra del confronto: una forma diversa, per avere un viaggio vero. */
  private fun elsewhere(slab: FluidForm.Slab): FluidForm.Slab {
    val f = slab.frame
    return FluidForm.Slab(
      frame = Rect(f.left - px(20f), f.top - px(10f), f.right + px(40f), f.bottom + px(30f)),
      cornerRadii = FluidCornerRadii.all(px(12f)),
      smoothing = ContinuousCornerShape.IosSmoothing,
    )
  }

  enum class RestVersusTravel(val suffix: String) {
    /** La lastra di `glassSurface`: è il metro. */
    Glass("lastra"),

    /** La stessa lastra come superficie fisica ferma: corpo a otto pezzi, luce sul nodo. */
    PhysicsRest("fisica-ferma"),

    /** Il primo fotogramma di un viaggio che parte da lei: luce in corpo. */
    TravelStart("viaggio-0"),

    /** L'ultimo fotogramma di un viaggio che arriva a lei. */
    TravelEnd("viaggio-1"),
  }

  private fun restVersusTravel(which: RestVersusTravel, theme: Theme, shadow: Boolean = false): GlassScene = scene(
    path = if (shadow) "riposo-contro-viaggio-ombra-${which.suffix}" else "riposo-contro-viaggio-${which.suffix}",
    note = "le stesse tre lastre (tasto 44 dp, capsula 156 x 56, menu 170 x 192) rese da quattro percorsi: la lastra di " +
      "`glassSurface`, la superficie fisica ferma, il primo e l'ultimo fotogramma di un viaggio. Questa è «${which.suffix}». " +
      "Si confrontano con `compare`: fuori dall'ombra devono coincidere entro un livello.",
    theme = theme,
    width = 480,
    bandHeight = 230,
  ) { top, band ->
    // Con l'ombra il palco della superficie fisica la deve contenere (come i palchi grandi dell'engine).
    val stage = if (shadow) 100f else 24f
    Slabs.map { slab ->
      val form = slab.form(top)
      val surface = sceneSurface(slab.benchRole)
      val name = "${slab.name}-$band"
      when (which) {
        RestVersusTravel.Glass -> {
          val f = form.frame
          val recipe = glassRecipe(
            role = surface.role,
            widthDp = f.width / Density,
            heightDp = f.height / Density,
            darkSide = theme.dark,
            optics = surface.optics.sanitized(),
          )
          val shape = linkedMapOf<String, Any?>(
            "name" to name, "x" to f.left / Density, "y" to f.top / Density, "w" to f.width / Density, "h" to f.height / Density,
            "radius" to slab.radius,
          )
          val made = liquidElement(shape, recipe, theme = null, note = "glassSurface, ${slab.benchRole}")
          val element = if (shadow) made else withoutShadow(made)
          // La stessa risoluzione della superficie fisica: è la sola cosa che il confronto non deve misurare.
          @Suppress("UNCHECKED_CAST")
          (element["capture"] as MutableMap<String, Any?>)["scale"] = 1
          element
        }
        RestVersusTravel.PhysicsRest -> physics(name, restPlan(form), surface.role, surface.optics, theme.dark, rest = true, shadow = shadow, stageMarginDp = stage)
        RestVersusTravel.TravelStart -> {
          val plan = PhysicsRenderPlan().also { buildSlabPlan(it, listOf(form to elsewhere(form)), 0f, t = 0f) }
          physics(name, plan, surface.role, surface.optics, theme.dark, rest = false, shadow = shadow, stageMarginDp = stage)
        }
        RestVersusTravel.TravelEnd -> {
          val plan = PhysicsRenderPlan().also { buildSlabPlan(it, listOf(elsewhere(form) to form), 0f, t = 1f) }
          physics(name, plan, surface.role, surface.optics, theme.dark, rest = false, shadow = shadow, stageMarginDp = stage)
        }
      }
    }
  }

  // ---- angolo-oltre-meta -----------------------------------------------------------------------------------------

  /** Le forme di `GlassFieldOutlineTest`: nome, taglia in dp, i quattro raggi chiesti. */
  val WideShapes: List<Triple<String, Pair<Float, Float>, List<Float>>> = listOf(
    Triple("fumetto-40", 220f to 40f, listOf(22f, 22f, 22f, 6f)),
    Triple("fumetto-44", 220f to 44f, listOf(22f, 22f, 22f, 6f)),
    Triple("angolo-80", 200f to 100f, listOf(80f, 20f, 20f, 20f)),
    Triple("foglia", 100f to 100f, listOf(80f, 20f, 80f, 20f)),
    Triple("coppia", 240f to 100f, listOf(70f, 12f, 12f, 40f)),
  )

  private fun wideCorners(theme: Theme): GlassScene = scene(
    path = "angolo-oltre-meta",
    note = "lastre di `glassSurface` con un angolo più largo di mezzo lato corto (`RoundedCornerShape`, le forme di " +
      "`GlassFieldOutlineTest`): il fumetto di chat 22/22/22/6 alto 40 e 44 dp, un angolo a 80 su un lato di 100, una " +
      "foglia 80/20/80/20, una coppia ridotta da Compose. Fino a F1B.1 restavano `Classic`.",
    theme = theme,
    width = 640,
    bandHeight = 250,
  ) { top, band ->
    val places = listOf(20f to 16f, 20f to 72f, 20f to 132f, 260f to 132f, 380f to 16f)
    WideShapes.mapIndexed { i, (name, size, radii) ->
      val (x, y) = places[i]
      val recipe = glassRecipe(
        role = GlassRole.Content,
        widthDp = size.first,
        heightDp = size.second,
        darkSide = theme.dark,
        optics = GlassDefaults.optics(GlassRole.Content).sanitized(),
      )
      val shape = linkedMapOf<String, Any?>(
        "name" to "$name-$band", "x" to x, "y" to top + y, "w" to size.first, "h" to size.second, "radius" to radii,
      )
      val element = withoutShadow(liquidElement(shape, recipe, theme = null, note = "glassSurface, Content, raggi $radii", continuous = false))
      @Suppress("UNCHECKED_CAST")
      (element["capture"] as MutableMap<String, Any?>)["scale"] = 1
      element
    }
  }

  // ---- poly ------------------------------------------------------------------------------------------------------

  private fun poly(theme: Theme): GlassScene = scene(
    path = "poly",
    note = "tre sagome libere ferme (`FluidForm.Poly`: stella, macchia, trapezio) rese dal corpo ad anello " +
      "(`GlassBodyRing`), con la luce in corpo: un anello non ha un programma di luce a riposo.",
    theme = theme,
    width = 480,
    bandHeight = 200,
  ) { top, band ->
    fun frame(left: Float, size: Float) = Rect(px(left), px(top + 30f), px(left + size), px(top + 30f + size))
    listOf(
      "stella" to FluidFormPresets.star(frame(20f, 140f), rounding = px(8f)),
      "macchia" to FluidFormPresets.blob(frame(170f, 140f), seed = 3, rounding = px(10f)),
      "trapezio" to FluidFormPresets.trapezoid(frame(320f, 140f), rounding = px(12f)),
    ).map { (name, form) ->
      physics("$name-$band", restPlan(form), GlassRole.Floating, GlassDefaults.optics(GlassRole.Floating), theme.dark, rest = true)
    }
  }

  // ---- fluidphysics-gruppo-nuovo -------------------------------------------------------------------------------

  /**
   * I due pezzi di `scenes/fluidphysics-gruppo-oggi.json` (una capsula 120 x 56 e un tondo da 56, a 2 dp, fusione
   * 18 dp, sullo stesso testo) col materiale nuovo: è il «dopo» di quella scena.
   */
  private fun groupOfToday(theme: Theme): GlassScene {
    val pieces = listOf(capsule(130f, 107f, 120f, 56f), circle(252f, 107f, 56f))
    val json = LinkedHashMap<String, Any?>()
    json["_"] = "Scritta dall'engine (F1bSceneExportTest, F1B.1): i due pezzi di `scenes/fluidphysics-gruppo-oggi.json` " +
      "(capsula e tondo a 2 dp, fusione 18 dp, a metà viaggio) col materiale nuovo. Senza ombra. Non si modifica a mano."
    json["width"] = 480
    json["height"] = 270
    json["density"] = Density
    json["theme"] = theme.name
    json["palette"] = "engine"
    json["corner"] = "engine"
    json["background"] = linkedMapOf(
      "kind" to "text", "background" to if (theme.dark) "#17171A" else "#E3E3E5", "color" to theme.ink, "fontSizeDp" to 30,
      "lineHeightDp" to 42, "paddingDp" to 18,
      "text" to "Signs are made using neon lights. Light is used to define color, tone, and shape. They are irreplaceable, " +
        "and depend on the people who bend them all over the world.",
    )
    json["elements"] = listOf(
      physics("gruppo", travelPlan(pieces, 18f), GlassRole.Floating, GlassDefaults.optics(GlassRole.Floating), theme.dark, rest = false, stageMarginDp = 60f),
    )
    return GlassScene("fluidphysics-gruppo-nuovo-${theme.suffix}.json", json)
  }

  /** F1B.2: le scene di [RestVersusTravel] con l'ombra accesa, a maschere e nel corpo (non sostituiscono quelle di F1B.1). */
  fun restVersusTravelWithShadow(): List<GlassScene> = Themes.flatMap { theme ->
    RestVersusTravel.entries.map { restVersusTravel(it, theme, shadow = true) }
  }

  fun all(): List<GlassScene> = Themes.flatMap { theme ->
    listOf(group(2, theme), group(4, theme), group(8, theme), bridge(theme)) +
      RestVersusTravel.entries.map { restVersusTravel(it, theme) } +
      listOf(wideCorners(theme), poly(theme), groupOfToday(theme))
  }
}
