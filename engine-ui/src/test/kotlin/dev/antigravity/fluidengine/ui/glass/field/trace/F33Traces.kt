package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDp
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneNeck
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneNeckPinch
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneShape
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildPlanePlan
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidInputs
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRecipeShortPx
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassMaterializeChannels
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFrame
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.glassMaterializeChannels
import dev.antigravity.fluidengine.ui.glass.field.glassMaterializeRecipe
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelRecipe
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneMotion
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneNeckMs
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/*
 * Le tracce e le scene di F3.3 (`GLASS_F33_DIR`, fase `f3-3`): il piano flottante di F3.1 **col collo** e **con la
 * comparsa per lente**. La geometria è quella di produzione (`GlassPlaneJourney`), il collo è `PhysicsPlaneNeck` lungo
 * il doppio del riposo a vista del viaggio (lo stesso orologio del contenitore, F3.2c), il piano è `buildPlanePlan` (quello della guida
 * «a fotogramma» del contenitore), il materiale è `glassRecipe` più `glassMaterializeRecipe`, gli uniform li scrive
 * `physicsLiquidInputs` col campo di `physicsFieldPlan`: niente qui è una seconda descrizione.
 *
 * Una traccia può avere più **corsie** affiancate (le varianti delle tavole di gusto, ognuna col suo viaggio e il suo
 * palco) e più **tappe** (uno stato nuovo a un dato istante: se il viaggio di prima non è finito è un'interruzione, e
 * collo e geometria ripartono da dove sono). Le scene ferme sono tracce di un fotogramma: `glassmotion.py scene` le
 * porta in `tools/glassbench/scenes/f3/`.
 */
internal object F33Traces {
  const val Phase = "f3-3"
  const val Fps = 60

  /** Le varianti di gusto: distanza di fusione, quanto si strozza il collo, scala iniziale della comparsa. */
  internal data class Variant(
    val label: String = "",
    val fusionDp: Float = PhysicsPlaneFusionDp,
    /** Di serie quello del piano flottante: «spesso», 1/2 (scelta di Alessio, 2026-11-16). */
    val pinch: Float = PhysicsPlaneNeckPinch,
    /** Vero per la «larga» della tavola (a): fusi anche a riposo quando sono vicini (contro la decisione dell'11/11). */
    val restFused: Boolean = false,
    val contourScale: Float = 1.09f,
  )

  /** Uno stato nuovo all'istante [atMs]. */
  internal class Leg(val atMs: Double, val state: GlassPlaneState)

  /** Il simbolo che sta sui pezzi che compaiono o scompaiono: l'icona e il suo colore. */
  internal class Symbol(val icon: String, val color: String, val sizeFraction: Float = 0.45f)

  internal class Lane(
    val name: String,
    val initial: GlassPlaneState,
    val legs: List<Leg>,
    val variant: Variant = Variant(),
    val symbol: Symbol? = null,
  )

  internal class Scenario(
    val name: String,
    val note: String,
    val widthDp: Float,
    val heightDp: Float,
    val density: Float,
    val dark: Boolean,
    val background: Map<String, Any?>,
    val lanes: List<Lane>,
    val firstFrame: Int = -5,
    val lastFrame: Int = 30,
    val page: List<Map<String, Any?>> = emptyList(),
    val apple: Any? = null,
    val probes: List<Map<String, Any?>> = emptyList(),
  )

  /** Un pezzo fermo di una scena: le lastre (pixel della tela), la coordinata del collo, `materialize`. */
  internal class Still(
    val name: String,
    val slabs: List<FluidForm.Slab>,
    val neck: Float = -1f,
    val materialize: Float = 1f,
    val fusionDp: Float = PhysicsPlaneFusionDp,
    val symbol: Symbol? = null,
    /** Il riquadro del contenuto (pixel della tela); di serie l'unione delle lastre. */
    val content: Rect? = null,
  )

  internal class StillScene(
    val name: String,
    val note: String,
    val widthDp: Float,
    val heightDp: Float,
    val density: Float,
    val dark: Boolean,
    val background: Map<String, Any?>,
    val items: List<Still>,
    val page: List<Map<String, Any?>> = emptyList(),
  )

  // ---- Il viaggio di una corsia --------------------------------------------------------------------------------

  /** Una tappa in corso: il viaggio, l'orologio del collo, da quando. */
  internal class Run(val startMs: Double, val journey: GlassPlaneJourney, val neck: PhysicsPlaneNeck)

  /** Le tappe di [lane], con le interruzioni risolte come le risolverà il contenitore (F3.2). */
  fun runs(lane: Lane): List<Run> {
    val motion = GlassPlaneMotion(initialContourScale = lane.variant.contourScale)
    val out = ArrayList<Run>()
    var state = lane.initial
    for (leg in lane.legs) {
      val prev = out.lastOrNull()
      val run = if (prev == null) {
        val journey = GlassPlaneJourney(state, leg.state, motion)
        Run(leg.atMs, journey, PhysicsPlaneNeck(-1f, physicsPlaneNeckMs(journey.settleMs)))
      } else {
        val local = leg.atMs - prev.startMs
        val journey = if (local >= prev.journey.durationMs) GlassPlaneJourney(state, leg.state, motion)
          else prev.journey.retarget(local, leg.state)
        Run(leg.atMs, journey, prev.neck.retarget(local, physicsPlaneNeckMs(journey.settleMs)))
      }
      out += run
      state = leg.state
    }
    return out
  }

  /** La tappa viva a [tMs] e il tempo dentro di lei (mai negativo: prima della prima tappa è il suo istante zero). */
  fun at(runs: List<Run>, tMs: Double): Pair<Run, Double> {
    val run = runs.lastOrNull { tMs >= it.startMs } ?: runs.first()
    return run to max(0.0, tMs - run.startMs)
  }

  /** La coordinata del collo di [lane] a [tMs] (fusi a riposo per la variante «larga»). */
  fun neckAt(lane: Lane, runs: List<Run>, tMs: Double): Float {
    if (lane.variant.restFused) return 1f
    val (run, local) = at(runs, tMs)
    return run.neck.at(local)
  }

  // ---- Un campo, un elemento ------------------------------------------------------------------------------------

  private val ButtonOptics = GlassDefaults.buttonOptics().sanitized()

  /**
   * L'elemento `material` di un campo: [slabs] in pixel della tela, fusi col collo [neck] e la distanza [fusionDp],
   * al `materialize` [amount]. Il palco è l'unione dei pezzi (o [stage]) allargata di quanto arriva l'ombra in viaggio.
   */
  fun fieldElement(
    name: String,
    slabs: List<FluidForm.Slab>,
    neck: Float,
    amount: Float,
    fusionDp: Float,
    pinch: Float,
    density: Float,
    dark: Boolean,
    widthDp: Float,
    heightDp: Float,
    stage: Rect? = null,
    content: List<Map<String, Any?>> = emptyList(),
    note: String = "",
    /** Il peso del materiale in viaggio ([physicsPlaneMotion]): zero da fermi. */
    motion: Float = 0f,
  ): LinkedHashMap<String, Any?> {
    val plan = PhysicsRenderPlan()
    // Il piano della guida «a fotogramma» del contenitore (F3.2c), con le stesse lastre, lo stesso collo e lo stesso
    // esponente: a riposo il raccordo continuo, come il riposo del contenitore.
    buildPlanePlan(plan, PhysicsPlaneShape(slabs, fusionDp * density, neck = neck, pinch = pinch, resting = true))
    val shortDp = physicsRecipeShortPx(plan) / density
    val recipe = glassRecipe(
      role = GlassRole.Interactive, widthDp = shortDp, heightDp = shortDp, darkSide = dark, optics = ButtonOptics,
      intensity = 1f,
    )
    // Il vetro in viaggio (scelta B, «un po' limpido»), poi la comparsa: l'ordine di `physicsLiquidRecipe`.
    glassTravelRecipe(recipe, GlassLiquidProfile.Default.travel, motion)
    glassMaterializeRecipe(recipe, amount)
    val padding = glassTravelPaddingDp(GlassRole.Interactive, ButtonOptics)
    val inputs = physicsLiquidInputs(
      plan = plan, recipe = recipe, scale = 1f, screenDensity = density,
      paddingPx = floor(max(padding * density, 0f)), out = GlassDrawInputs(GlassFieldMaxPieces), neckPinch = pinch,
    )
    val reach = (glassTravelShadowReachDp(ButtonOptics, gain = 0f) + 2f) * density
    val union = stage ?: plan.bounds
    val box = Rect(union.left - reach, union.top - reach, union.right + reach, union.bottom + reach)
    val element = liquidPhysicsElement(name, inputs, Offset.Zero, density, widthDp, heightDp, content, stage = box, note = note)
    if (slabs.size == 1) narrowToOne(element)
    return element
  }

  /** Lo strato del simbolo sopra [box] (pixel della tela), ai canali della comparsa di [amount]. */
  fun symbolLayer(symbol: Symbol, box: Rect, amount: Float, density: Float): Map<String, Any?>? {
    val c = glassMaterializeChannels(amount, GlassMaterializeChannels())
    if (c.symbolAlpha <= 0f) return null
    val side = box.minDimension / density
    return linkedMapOf(
      "name" to "simbolo",
      "x" to round3(box.left / density), "y" to round3(box.top / density),
      "w" to round3(box.width / density), "h" to round3(box.height / density),
      "icon" to symbol.icon, "iconSizeDp" to round3(side * symbol.sizeFraction), "color" to symbol.color,
      "alpha" to round3(c.symbolAlpha), "scale" to round3(c.symbolScale),
    )
  }

  // ---- Le tracce -------------------------------------------------------------------------------------------------

  fun build(scenario: Scenario): GlassTrace {
    val d = scenario.density
    val laneRuns = scenario.lanes.map { runs(it) }
    val frames = (scenario.firstFrame..scenario.lastFrame).map { index ->
      val time = glassTraceTimeMs(index, Fps)
      val elements = ArrayList<Map<String, Any?>>()
      scenario.lanes.forEachIndexed { laneIndex, lane ->
        val runs = laneRuns[laneIndex]
        val (run, local) = at(runs, max(time, 0.0))
        val frame: GlassPlaneFrame = run.journey.frame(local)
        val neck = neckAt(lane, runs, max(time, 0.0))
        frame.fields.forEachIndexed { fieldIndex, field ->
          val flying = field.flights.map { id -> frame.pieces.single { it.flightId == id } }
          // Un pezzo che è scomparso del tutto non si disegna: la sua lastra resta a zero nel campo, invisibile.
          val slabs = flying.map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) }
          val amount = flying.minOf { it.materialize }
          val content = lane.symbol?.let { symbol ->
            flying.mapNotNull { symbolLayer(symbol, it.contentBounds, it.materialize, d) }
          }.orEmpty()
          // Il lato più veloce del campo, in dp al secondo: il peso del vetro in viaggio.
          val speed = flying.maxOf { p ->
            val v = p.velocity.bounds
            maxOf(maxOf(abs(v.left), abs(v.right)), maxOf(abs(v.top), abs(v.bottom)))
          } / d
          val motion = physicsPlaneMotion(speed, max(time, 0.0).toFloat())
          val element = fieldElement(
            name = "${lane.name}$fieldIndex", slabs = slabs, neck = neck, amount = amount,
            fusionDp = lane.variant.fusionDp, pinch = lane.variant.pinch, density = d, dark = scenario.dark,
            widthDp = scenario.widthDp, heightDp = scenario.heightDp, stage = field.bounds, content = content,
            note = "F3.3: piano di F3.1, collo nel tempo (PhysicsPlaneNeck), comparsa per lente (glassMaterializeRecipe)",
            motion = motion,
          )
          element["_kernel"] = field.kernel.name
          element["_collo"] = round3(neck)
          element["_moto"] = round3(motion)
          element["_piano"] = flying.map { piece ->
            linkedMapOf(
              "key" to piece.piece.key.toString(), "change" to piece.change.name, "materialize" to round3(piece.materialize),
              "rectDp" to listOf(piece.bounds.left, piece.bounds.top, piece.bounds.right, piece.bounds.bottom)
                .map { round3(it / d) },
            )
          }
          elements += element
        }
      }
      GlassTraceFrame(time, elements)
    }
    val head = linkedMapOf<String, Any?>(
      "_" to "F3.3: ${scenario.note}",
      "palette" to "engine",
      "background" to scenario.background,
    )
    if (scenario.page.isNotEmpty()) head["page"] = scenario.page
    val events = scenario.lanes.flatMap { lane -> lane.legs.drop(1).map { it.atMs } }.distinct().sorted()
    head["events"] = events.map { linkedMapOf("t" to it, "kind" to "interrupt", "label" to "stato nuovo") }
    scenario.apple?.let { head["apple"] = it }
    if (scenario.probes.isNotEmpty()) head["probes"] = scenario.probes
    return GlassTrace(
      name = scenario.name, phase = Phase, density = d, theme = if (scenario.dark) "dark" else "light",
      widthDp = scenario.widthDp, heightDp = scenario.heightDp, fps = Fps, frames = frames, head = head,
    )
  }

  fun buildStill(scene: StillScene): GlassTrace {
    val d = scene.density
    val elements = scene.items.map { item ->
      val content = item.symbol?.let { symbol ->
        val box = item.content ?: item.slabs.map { it.frame }.reduce { a, b ->
          Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }
        listOfNotNull(symbolLayer(symbol, box, item.materialize, d))
      }.orEmpty()
      val element = fieldElement(
        name = item.name, slabs = item.slabs, neck = item.neck, amount = item.materialize, fusionDp = item.fusionDp,
        pinch = PhysicsPlaneNeckPinch, density = d, dark = scene.dark, widthDp = scene.widthDp,
        heightDp = scene.heightDp, content = content, note = "F3.3: scena ferma scritta dall'engine",
      )
      element["_collo"] = round3(item.neck)
      element["_materialize"] = round3(item.materialize)
      element
    }
    val head = linkedMapOf<String, Any?>(
      "_" to "F3.3: ${scene.note}. Scritta dall'engine (F33ExportTest): non si modifica a mano, si rigenera.",
      "palette" to "engine",
      "background" to scene.background,
    )
    if (scene.page.isNotEmpty()) head["page"] = scene.page
    return GlassTrace(
      name = scene.name, phase = Phase, density = d, theme = if (scene.dark) "dark" else "light",
      widthDp = scene.widthDp, heightDp = scene.heightDp, fps = Fps, frames = listOf(GlassTraceFrame(0.0, elements)),
      head = head,
    )
  }

  /** Lo scrittore fisico prepara otto caselle: Body1 ha gli stessi uniform con una casella (come in F3.1). */
  @Suppress("UNCHECKED_CAST")
  private fun narrowToOne(element: MutableMap<String, Any?>) {
    val body = element.getValue("body") as MutableMap<String, Any?>
    body["source"] = (body.getValue("source") as String).replace("8ShaderString", "1ShaderString")
    val uniforms = body.getValue("uniforms") as MutableMap<String, Any?>
    listOf("pieceRect", "pieceRadii", "pieceBlend", "pieceSmooth").forEach { name ->
      uniforms[name] = (uniforms.getValue(name) as List<Any?>).take(1)
    }
    uniforms.remove("pieceSoft")
  }

  // ---- Le geometrie ----------------------------------------------------------------------------------------------

  private fun piece(
    d: Float, id: String, x: Float, y: Float, w: Float = 44f, h: Float = 44f,
    transition: GlassPlaneTransition = GlassPlaneTransition.MatchedGeometry,
  ) = GlassPlanePiece(
    key = id, id = id, bounds = Rect(x * d, y * d, (x + w) * d, (y + h) * d),
    radii = FluidCornerRadii.all(minOf(w, h) / 2f * d), transition = transition,
  )

  private fun state(d: Float, widthDp: Float, heightDp: Float, vararg pieces: GlassPlanePiece) =
    GlassPlaneState(pieces.toList(), 12f * d, Rect(0f, 0f, widthDp * d, heightDp * d))

  private fun circle(d: Float, x: Float, y: Float, side: Float = 44f) =
    FluidForm.Slab(Rect(x * d, y * d, (x + side) * d, (y + side) * d), FluidCornerRadii.all(side / 2f * d), smoothing = 0f)

  private fun text(dark: Boolean): Map<String, Any?> = linkedMapOf(
    "kind" to "text", "background" to if (dark) "#17171A" else "#E3E3E5", "color" to if (dark) "#F2F2F7" else "#17171A",
    "fontSizeDp" to 18, "lineHeightDp" to 26, "paddingDp" to 12,
    "text" to "Il piano porta il vetro da uno stato al successivo. Il testo sotto resta fermo. ".repeat(14),
  )

  private fun grid(dark: Boolean): Map<String, Any?> = linkedMapOf(
    "kind" to "grid", "stepDp" to 10, "lineDp" to 1.5,
    "color" to if (dark) "#D6D6DC" else "#1E1E24", "background" to if (dark) "#202024" else "#ECECF0",
  )

  private val Dunes: Map<String, Any?> = linkedMapOf(
    "kind" to "synthetic", "palette" to listOf("#4A2E17", "#8C6239", "#C49A6C", "#E6CBA2", "#6E4A2A"),
    "scaleDp" to 110, "detail" to 0.35,
  )

  private val Flat: Map<String, Any?> = linkedMapOf("kind" to "color", "color" to "#D9D9DE")

  private fun label(text: String, x: Float, y: Float, dark: Boolean = false): Map<String, Any?> = linkedMapOf(
    "kind" to "text", "text" to text, "x" to x, "y" to y, "sizeDp" to 15, "color" to if (dark) "#F2F2F7" else "#111114",
  )

  private val Select = linkedMapOf(
    "name" to "select-done", "video" to "wwdc2025-219_hd", "t0Frame" to 9055, "crop" to listOf(300, 250, 1320, 587),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B2_select_done_f9029-9076.jpg",
    "note" to "R4 B2 (d), classe C rallentata: t0 al collo che comincia ad assottigliarsi (f9055); vale l'ordine delle fasi.",
  )
  private val Tinted = linkedMapOf(
    "name" to "tasto-tinto", "video" to "wwdc2025-219_hd", "t0Frame" to 30450, "crop" to listOf(120, 200, 1350, 600),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B8_divisione_tasto_tinto_f30440-30500.jpg",
    "note" to "R4 B8: inquadrato sotto la barra, vale solo per l'alone.",
  )
  private val Context = linkedMapOf(
    "name" to "contesto", "video" to "wwdc2025-219_hd", "t0Frame" to 28857, "crop" to listOf(330, 420, 600, 245),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B2_barra_contesto_16m02_f28855-28890.jpg",
    "note" to "R4 B2 (c): la capsula di destra si chiude; il render zooma un poco, vale la forma.",
  )
  private val Appear = linkedMapOf(
    "name" to "comparsa", "video" to "wwdc2025-219_hd", "t0Frame" to 5399, "crop" to listOf(442, 197, 1050, 700),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B3_comparsa_play_f5397-5420.jpg",
    "note" to "R4 B3, media: comparsa 430 ms, contorno prima, simbolo da 2x a 1x.",
  )
  private val Disappear = linkedMapOf(
    "name" to "scomparsa", "video" to "wwdc2025-219_hd", "t0Frame" to 5368, "crop" to listOf(442, 197, 1050, 700),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B3_comparsa_play_f5290-5400_ogni2.jpg",
    "note" to "R4 B3, media: scomparsa 470 ms, simbolo via in 130, rifrazione a zero entro 400, poi il contorno.",
  )

  /** Le tracce del movimento. */
  fun scenarios(): List<Scenario> {
    val d = 3f
    val w = 360f
    val h = 160f
    fun st(vararg p: GlassPlanePiece) = state(d, w, h, *p)
    // La geometria di F3.1 (i due pezzi finiscono a 40 dp) e quella vicina (8 dp, la distanza fra due vetri di un gruppo).
    val one = st(piece(d, "a", 144f, 58f))
    val two = st(piece(d, "a", 112f, 58f), piece(d, "b", 196f, 58f))
    val oneNear = st(piece(d, "a", 158f, 58f))
    val twoNear = st(piece(d, "a", 132f, 58f), piece(d, "b", 184f, 58f))
    fun lane(from: GlassPlaneState, vararg legs: Leg) = Lane("piano", from, legs.toList())
    val split = "un tasto che diventa due"
    val merge = "due tasti che tornano uno"
    val list = arrayListOf(
      // F3.12a: una nascita e una confluenza durano 500 ms (erano 345): le tracce arrivano a 600, e a 800 quelle
      // ripensate a metà, per avere la coda ferma che serve a guardare la consegna.
      Scenario("collo-uno-due", "$split (geometria di F3.1, 40 dp a riposo)", w, h, d, false, text(false),
        listOf(lane(one, Leg(0.0, two))), lastFrame = 36, apple = listOf(Select)),
      Scenario("collo-divisione", "$merge (geometria di F3.1)", w, h, d, false, text(false),
        listOf(lane(two, Leg(0.0, one))), lastFrame = 36, apple = listOf(Tinted)),
      Scenario("collo-uno-due-vicini", "$split, a 8 dp", w, h, d, false, text(false),
        listOf(lane(oneNear, Leg(0.0, twoNear))), lastFrame = 36, apple = listOf(Select)),
      Scenario("collo-divisione-vicini", "$merge, da 8 dp", w, h, d, false, text(false),
        listOf(lane(twoNear, Leg(0.0, oneNear))), lastFrame = 36, apple = listOf(Tinted)),
      Scenario("collo-uno-due-vicini-scuro", "$split, a 8 dp, tema scuro", w, h, d, true, text(true),
        listOf(lane(oneNear, Leg(0.0, twoNear))), lastFrame = 36),
      Scenario("collo-uno-due-interrotto", "$split, ripensato a 150 ms (F3.1)", w, h, d, false, text(false),
        listOf(lane(one, Leg(0.0, two), Leg(150.0, one))), lastFrame = 48),
      Scenario("collo-divisione-interrotta", "$merge, ripensato a 150 ms (F3.1)", w, h, d, false, text(false),
        listOf(lane(two, Leg(0.0, one), Leg(150.0, two))), lastFrame = 48),
      Scenario("collo-uno-due-vicini-interrotto", "$split a 8 dp, ripensato a 80 ms, col collo vivo", w, h, d, false,
        text(false), listOf(lane(oneNear, Leg(0.0, twoNear), Leg(80.0, oneNear))), lastFrame = 48),
      Scenario("collo-divisione-vicini-interrotta", "$merge da 8 dp, ripensato a 50 ms, col collo vivo", w, h, d, false,
        text(false), listOf(lane(twoNear, Leg(0.0, oneNear), Leg(50.0, twoNear))), lastFrame = 48),
      Scenario("contesto-capsule-curva", "la capsula di F3.1 che si stringe da 200 a 88 dp", w, h, d, false, Flat,
        listOf(lane(st(piece(d, "capsula", 120f, 58f, 200f)), Leg(0.0, st(piece(d, "capsula", 232f, 58f, 88f))))),
        apple = listOf(Context),
        probes = listOf(linkedMapOf(
          "name" to "larghezza", "kind" to "width", "line" to "h:80:100:340", "mode" to "light",
          "ref" to linkedMapOf("overshootPct" to listOf(0, 1.3), "restMs" to listOf(270, 330), "source" to "R4 B2 (c), media"),
        ))),
    )
    // La comparsa: un tasto di riproduzione da 64 dp sopra le dune (R4 B3), e un tasto da 44 dp sul testo (`w` a 6x).
    val cw = 240f
    val ch = 160f
    fun cs(vararg p: GlassPlanePiece) = state(d, cw, ch, *p)
    val play = piece(d, "play", 88f, 48f, 64f, 64f, GlassPlaneTransition.Materialize)
    val playSymbol = Symbol("fluid:play_arrow|plus", "#FFFFFF")
    list += Scenario("comparsa-lente", "comparsa per lente di un tasto di riproduzione da 64 dp", cw, ch, d, false, Dunes,
      listOf(Lane("piano", cs(), listOf(Leg(0.0, cs(play))), symbol = playSymbol)), lastFrame = 36, apple = listOf(Appear))
    list += Scenario("scomparsa-lente", "scomparsa per lente dello stesso tasto", cw, ch, d, false, Dunes,
      listOf(Lane("piano", cs(play), listOf(Leg(0.0, cs())), symbol = playSymbol)), lastFrame = 36,
      apple = listOf(Disappear))
    val small = piece(d, "play", 158f, 58f, transition = GlassPlaneTransition.Materialize)
    list += Scenario("comparsa-su-testo", "comparsa di un tasto da 44 dp sul testo: l'immagine doppia della mescola `w`",
      w, h, d, false, text(false),
      listOf(Lane("piano", st(), listOf(Leg(0.0, st(small))), symbol = Symbol("fluid:play_arrow|plus", "#17171A"))),
      lastFrame = 36)
    return list
  }

  /** Le tracce delle tre tavole di gusto: varianti affiancate, ognuna nella sua corsia. */
  fun tastes(): List<Scenario> {
    val d = 2f
    val w = 600f
    val h = 190f
    fun st(vararg p: GlassPlanePiece) = state(d, w, h, *p)
    fun laneSplit(x0: Float, variant: Variant, name: String): Lane {
      val one = st(piece(d, "$name-a", x0 + 78f, 92f))
      val two = st(piece(d, "$name-a", x0 + 52f, 92f), piece(d, "$name-b", x0 + 104f, 92f))
      // La riunione parte quando la divisione è finita e ferma: 650 ms (una nascita dura 500 da F3.12a; erano 550).
      return Lane(name, one, listOf(Leg(0.0, two), Leg(650.0, one)), variant)
    }
    val fusion = listOf(Variant("stretta · 8 dp", fusionDp = 8f), Variant("media · 16 dp (proposta)", fusionDp = 16f),
      Variant("larga · 24 dp, fusi a riposo", fusionDp = 24f, restFused = true))
    // La tavola com'era quando Alessio l'ha giudicata (2026-11-16): ha scelto la prima, che oggi è di serie.
    val pinch = listOf(Variant("spesso · si spezza di colpo", pinch = 0.5f),
      Variant("di serie · 2/3 (proposta)", pinch = 2f / 3f),
      Variant("filo · si assottiglia a lungo", pinch = 1f))
    val scale = listOf(Variant("1,09 · R4 (proposta)", contourScale = 1.09f), Variant("1,045", contourScale = 1.045f),
      Variant("1,00 · niente scala", contourScale = 1f))
    // Le etichette su una fascia chiara in cima, sopra le corsie: sul testo della pagina non si leggerebbero.
    val band = linkedMapOf<String, Any?>("kind" to "rect", "x" to 0, "y" to 0, "w" to w, "h" to 44, "color" to "#F4F4F6")
    fun labels(variants: List<Variant>) = listOf(band) + variants.mapIndexed { i, v -> label(v.label, i * 200f + 14f, 28f) }
    val result = ArrayList<Scenario>()
    result += Scenario("gusto-fusione", "tavola (a): distanza di fusione di serie; divisione a 0 ms, riunione a 650 ms",
      w, h, d, false, text(false), fusion.mapIndexed { i, v -> laneSplit(i * 200f, v, "f$i") },
      lastFrame = 78, page = labels(fusion))
    result += Scenario("gusto-collo", "tavola (b): quanto si strozza il collo (il collo lungo, A, in tutte)",
      w, h, d, false, text(false), pinch.mapIndexed { i, v -> laneSplit(i * 200f, v, "c$i") },
      lastFrame = 78, page = labels(pinch))
    val playSymbol = Symbol("fluid:play_arrow|plus", "#FFFFFF")
    result += Scenario("gusto-comparsa", "tavola (c): scala iniziale della comparsa; compare a 0 ms, scompare a 600 ms",
      w, h, d, false, Dunes, scale.mapIndexed { i, v ->
        val p = piece(d, "s$i", i * 200f + 68f, 74f, 64f, 64f, GlassPlaneTransition.Materialize)
        Lane("s$i", st(), listOf(Leg(0.0, st(p)), Leg(600.0, st())), v, playSymbol)
      }, lastFrame = 72, page = labels(scale))
    return result
  }

  // ---- Le scene ferme -------------------------------------------------------------------------------------------

  /** Le cinque larghezze del collo: la coordinata del collo, dal pieno alla rottura. */
  val BridgeNecks: List<Float> = listOf(1f, 0.6f, 0.3f, 0.1f, 0.02f)

  /** I cinque istanti della comparsa ferma (`materialize`). */
  val AppearSteps: List<Float> = listOf(0f, 0.2f, 0.45f, 0.75f, 1f)

  fun stills(): List<StillScene> {
    val d = 3f
    val out = ArrayList<StillScene>()
    // Ponte: sopra due tasti a 8 dp (il collo fra pezzi staccati), sotto due tasti compenetrati di 6 dp (al contatto,
    // dove stava la cucitura di (a) e (c)). Cinque coordinate del collo, da sinistra a destra.
    fun bridges(dark: Boolean, background: Map<String, Any?>, name: String) = StillScene(
      name = name,
      note = "il collo a cinque larghezze (coordinata ${BridgeNecks.joinToString()}): sopra due tasti da 44 dp a 8 dp, " +
        "sotto compenetrati di 6 dp; fusione ${PhysicsPlaneFusionDp.toInt()} dp",
      widthDp = 530f, heightDp = 200f, density = d, dark = dark, background = background,
      items = BridgeNecks.flatMapIndexed { i, neck ->
        val x = 12f + i * 104f
        listOf(
          Still("staccati-$i", listOf(circle(d, x, 28f), circle(d, x + 52f, 28f)), neck = neck),
          Still("contatto-$i", listOf(circle(d, x + 4f, 124f), circle(d, x + 42f, 124f)), neck = neck),
        )
      },
    )
    out += bridges(false, grid(false), "ponte-griglia-chiaro")
    out += bridges(true, grid(true), "ponte-griglia-scuro")
    out += bridges(false, text(false), "ponte-testo-chiaro")
    out += bridges(false, Dunes, "ponte-foto-chiaro")
    // La comparsa ferma: cinque tasti a `materialize` 0, 0,2, 0,45, 0,75, 1, con la sagoma che parte 1,09 più larga.
    fun appear(name: String, background: Map<String, Any?>, side: Float, color: String) = StillScene(
      name = name,
      note = "la comparsa per lente a materialize ${AppearSteps.joinToString()}: contorno, poi lente e simbolo da 2x a 1x",
      widthDp = 5 * (side + 40f), heightDp = side + 70f, density = d, dark = false, background = background,
      items = AppearSteps.mapIndexed { i, m ->
        val cx = 20f + side / 2f + i * (side + 40f)
        val cy = 35f + side / 2f
        val grow = 1.09f - 0.09f * m
        val s = side * grow
        Still(
          "m$i", listOf(circle(d, cx - s / 2f, cy - s / 2f, s)), materialize = m,
          symbol = Symbol("fluid:play_arrow|plus", color),
          content = Rect((cx - side / 2f) * d, (cy - side / 2f) * d, (cx + side / 2f) * d, (cy + side / 2f) * d),
        )
      },
    )
    out += appear("comparsa-foto", Dunes, 64f, "#FFFFFF")
    out += appear("comparsa-testo", text(false), 44f, "#17171A")
    out += appear("comparsa-griglia", grid(false), 64f, "#17171A")
    // L'unione: due tasti sempre più compenetrati col collo pieno, fino a coincidere. Nessun alone oltre i lobi.
    val overlaps = listOf(0f, 6f, 15f, 30f, 44f)
    out += StillScene(
      name = "unione-chiaro",
      note = "due tasti da 44 dp compenetrati di ${overlaps.joinToString { it.toInt().toString() }} dp col collo pieno " +
        "(sopra) e a riposo (sotto): la fusione si attenua nella sovrapposizione, chi coincide è un tasto solo",
      widthDp = 530f, heightDp = 200f, density = d, dark = false, background = grid(false),
      items = overlaps.flatMapIndexed { i, o ->
        val x = 12f + i * 104f
        listOf(
          Still("viaggio-$i", listOf(circle(d, x, 28f), circle(d, x + 44f - o, 28f)), neck = 1f),
          Still("riposo-$i", listOf(circle(d, x, 124f), circle(d, x + 44f - o, 124f)), neck = -1f),
        )
      },
    )
    return out
  }
}
