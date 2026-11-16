package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentFlight
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPlaneDriver
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSwell
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentLook
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassPieceContent
import dev.antigravity.fluidengine.ui.fluid.GlassPieceContentRole
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassArrivalOf
import dev.antigravity.fluidengine.ui.fluid.fluidGlassContentFlightOf
import dev.antigravity.fluidengine.ui.fluid.fluidGlassFingerTouch
import dev.antigravity.fluidengine.ui.fluid.fluidGlassRestKey
import dev.antigravity.fluidengine.ui.fluid.fluidGlassRestingClusters
import dev.antigravity.fluidengine.ui.fluid.fluidGlassStagePlane
import dev.antigravity.fluidengine.ui.fluid.fluidGlassUnionIndices
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.glassPieceContentDone
import dev.antigravity.fluidengine.ui.fluid.glassPieceContentFor
import dev.antigravity.fluidengine.ui.fluid.glassPieceContentLook
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluid.unionsFirst
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDp
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneShape
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneTouch
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidGlow
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildPlanePlan
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidInputs
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRecipeShortPx
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourneys
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.glassJourneyRecipe
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneCluster
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFlightId
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFrame
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneUnionChange
import dev.antigravity.fluidengine.ui.glass.field.glassMaterializeRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import dev.antigravity.fluidengine.ui.glass.field.glassTravelRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp
import kotlin.math.floor
import kotlin.math.max

/*
 * Le tracce di F3.8a (`GLASS_F38A_DIR`, fase `f3-8a`): il piano flottante **col contenuto**, come lo porta il
 * contenitore dopo le correzioni di S3. Non è una seconda descrizione: il tempo è quello del conducente di produzione
 * (`FluidGlassPlaneDriver`, col collo e col collo delle unioni), la forma di ogni campo è `fluidGlassStagePlane`, chi
 * resta al vetro di riposo lo dice `fluidGlassRestingClusters`, e l'aspetto e il ritaglio del contenuto di ogni pezzo
 * vengono da `glassPieceContentFor`, `glassPieceContentLook` e `fluidGlassContentFlightOf`, le stesse funzioni che il
 * contenitore chiama a ogni fotogramma. Qui c'è solo il giro che le mette in fila (lo stesso di `startContents` e
 * `driveContents`, che nel contenitore lavorano sui nodi) e la scrittura degli strati.
 *
 * Il **carattere del viaggio** di F3.9 è quello del contenitore (F3.10): il rigonfiamento nelle lastre, la **luce** nella
 * ricetta del palco (`glassJourneyRecipe` al livello `journeyLight` del conducente, il margine della cattura del carattere
 * al colmo) e la **luce del dito** (`fluidGlassFingerTouch`, `physicsLiquidGlow`) quando la scena dice quale vetro è stato
 * toccato ([Scene.touch]). Di serie il carattere del profilo; una scena può darne un altro ([Scene.journey]: le colonne
 * delle tavole di F3.10). Le tracce di F3.8a già scritte in `traces/f3-8a/` sono di prima dell'aggancio (senza luce).
 *
 * Servono a guardare sul banco ciò che S3 ha visto sul tablet: «Fatto» che non è mai scritto fuori dal vetro, il
 * contenuto di chi se ne va che si sfoca dov'era, due tasti fermi che non si uniscono mentre un terzo compare,
 * «Unisci» e «Separa» come viaggi, la comparsa senza pausa. E a rifare la tavola per fasi contro Apple
 * (`artifacts/liquid-glass/sessioni/f3-8a/strumenti/tavola-fasi.py`).
 */
internal object F38aTraces {
  const val Phase = "f3-8a"
  const val Fps = 120

  /** Ciò che un pezzo porta scritto: un testo o un'icona. */
  internal class Label(val text: String? = null, val icon: String? = null, val sizeDp: Float = 17f)

  /** Uno stato nuovo all'istante [atMs] (su un fotogramma: il viaggio parte lì). */
  internal class Step(val atMs: Double, val state: GlassPlaneState)

  /**
   * Il tocco che fa partire il viaggio (F3.10): il pezzo [key], premuto ad [amount] quando lo stato cambia (il dito si è
   * appena alzato), col dito in ([u], [v]) del suo riquadro. Prima del cambio il vetro fermo mostra quel bagliore.
   */
  internal class Touch(val key: Any, val amount: Float, val u: Float = 0.5f, val v: Float = 0.5f)

  internal class Scene(
    val name: String,
    val note: String,
    val widthDp: Float,
    val heightDp: Float,
    val dark: Boolean,
    val initial: GlassPlaneState,
    val steps: List<Step>,
    val labels: Map<Any, Label>,
    val lastFrame: Int,
    val density: Float = 3f,
    val firstFrame: Int = -3,
    val spacingDp: Float = PhysicsPlaneFusionDp,
    val background: Map<String, Any?> = Dunes,
    val apple: Any? = null,
    /** Il carattere del viaggio (F3.9): di serie quello del profilo, come il contenitore. */
    val journey: GlassLiquidJourney = GlassLiquidProfile.Default.journey,
    /** Il tocco che fa partire il viaggio (F3.10), o null: un cambio scritto dall'app. */
    val touch: Touch? = null,
    /** La fase del banco in cui si scrive la traccia (`traces/<fase>/<nome>`). */
    val phase: String = Phase,
  )

  private val Dunes: Map<String, Any?> = linkedMapOf(
    "kind" to "synthetic", "palette" to listOf("#4A2E17", "#8C6239", "#C49A6C", "#E6CBA2", "#6E4A2A"),
    "scaleDp" to 110, "detail" to 0.35,
  )

  private val ButtonOptics = GlassDefaults.buttonOptics().sanitized()

  /** Il contenuto di un pezzo nel giro della traccia: ciò che nel contenitore sta in `FluidGlassPieceContent`. */
  private class Content(var place: Rect) {
    var role: GlassPieceContent = GlassPieceContent.Rest
    var look: GlassContentLook = GlassContentLook.Sharp
    var flight: FluidGlassContentFlight? = null
    var attached = true
    var shown = true
  }

  /** Un campo del palco, come lo tiene il contenitore dalla partenza all'arrivo del viaggio. */
  private class StageField(
    val index: Int,
    val flights: List<GlassPlaneFlightId>,
    val bounds: Rect,
    val union: GlassPlaneUnionChange,
    val lens: Boolean,
  )

  fun build(scene: Scene): GlassTrace {
    val d = scene.density
    val spacingPx = scene.spacingDp * d
    val swellMaxPx = FluidMotion.PlaneSwellMaxDp * d
    val motion = GlassPlaneMotion()
    val driver = FluidGlassPlaneDriver(character = { scene.journey })
    val contents = LinkedHashMap<Any, Content>()
    var stage: List<StageField> = emptyList()
    var resting: List<GlassPlaneCluster> = emptyList()
    var swell = FluidGlassSwell.None
    val base = 10_000_000_000L
    fun stateAt(tMs: Double): GlassPlaneState =
      scene.steps.lastOrNull { tMs >= it.atMs - 1e-6 }?.state ?: scene.initial

    val frames = (scene.firstFrame..scene.lastFrame).map { index ->
      val time = glassTraceTimeMs(index, Fps)
      val state = stateAt(time)
      val nanos = base + glassTraceTimeNanos(index, Fps)
      val change = driver.step(nanos, state, motion)
      // I pezzi che ci sono adesso e dove stanno; chi non c'è più resta dov'era, staccato.
      for (piece in state.pieces) {
        val content = contents.getOrPut(piece.key) {
          // Un pezzo nuovo in un piano già a riposo aspetta il fotogramma che decide come entra.
          Content(piece.bounds).also { if (index > scene.firstFrame) it.shown = false }
        }
        content.place = piece.bounds
        content.attached = true
      }
      for ((key, content) in contents) if (state.pieces.none { it.key == key }) content.attached = false

      val journey = driver.journey
      val frame: GlassPlaneFrame? = driver.frame
      if (journey == null || frame == null) {
        // A riposo: ogni contenuto è pieno e al suo posto, e niente resta di chi se n'è andato.
        stage = emptyList()
        resting = emptyList()
        swell = FluidGlassSwell.None
        contents.entries.removeAll { !it.value.attached }
        for (content in contents.values) {
          content.role = GlassPieceContent.Rest
          content.look = GlassContentLook.Sharp
          content.flight = null
          content.shown = true
        }
      } else {
        if (change == FluidGlassPlaneDriver.Change.Started || change == FluidGlassPlaneDriver.Change.Retargeted) {
          // La luce del dito: il vetro toccato, se la scena lo dice (`noteFinger` del contenitore). Al primo viaggio il
          // bagliore si vedeva sul vetro fermo se il pezzo era solo nel suo grappolo.
          val touch = scene.touch
          if (touch != null && change == FluidGlassPlaneDriver.Change.Started) {
            val shown = driver.origin?.clusters?.any { it.pieces.size == 1 && it.pieces.first().key == touch.key } == true
            driver.touched(touch.key, touch.amount, touch.u, touch.v, shown)
          }
          val target = driver.rest!!
          resting = driver.origin?.let { fluidGlassRestingClusters(it, target, journey.stillKeys) }.orEmpty()
          val restingKeys = resting.flatMapTo(HashSet()) { cluster -> cluster.pieces.map { it.key } }
          stage = journey.fields.mapIndexedNotNull { fieldIndex, field ->
            val flights = field.flights.filter { it.targetKey !in restingKeys }
            if (flights.isEmpty()) null else StageField(
              fieldIndex, flights, field.bounds, field.union,
              flights.all { it.change == GlassPlaneChange.Materialize || it.change == GlassPlaneChange.Dematerialize },
            )
          }
          // La parte del contenuto di ogni pezzo, dall'aspetto che ha: `startContents` del contenitore.
          for (flying in frame.pieces) {
            val arrival = fluidGlassArrivalOf(flying.change)
            val targetKey = flying.flightId.targetKey
            val sourceKey = flying.flightId.sourceKey
            if (targetKey != null) {
              val content = contents[targetKey] ?: continue
              content.role = glassPieceContentFor(arrival, content.look.takeIf { content.shown }, departed = false)
              content.shown = true
            } else if (sourceKey != null) {
              val content = contents[sourceKey]?.takeIf { !it.attached } ?: continue
              content.role = glassPieceContentFor(arrival, content.look.takeIf { content.shown }, departed = true)
            }
          }
          contents.entries.removeAll { !it.value.attached && it.value.role.role != GlassPieceContentRole.Exit }
        }
        swell = FluidGlassSwell(driver.journeyCharacter, driver.journeySwell, swellMaxPx, d)
        // L'aspetto di ogni contenuto in questo fotogramma: `driveContents` del contenitore.
        val done = ArrayList<Any>()
        for (flying in frame.pieces) {
          val key = flying.flightId.targetKey ?: flying.flightId.sourceKey ?: continue
          val content = contents[key] ?: continue
          if (flying.flightId.targetKey == null && content.attached) continue
          content.look = glassPieceContentLook(content.role, flying.contentPhase)
          if (flying.flightId.targetKey == null && glassPieceContentDone(content.role, flying.contentPhase)) {
            done += key
            continue
          }
          content.flight = fluidGlassContentFlightOf(flying, content.role, content.look, content.place, swell)
        }
        done.forEach { contents.remove(it) }
      }

      val elements = ArrayList<Map<String, Any?>>()
      val rest = driver.rest
      if (rest != null) {
        val carrying = if (journey == null) rest.clusters else resting
        for (cluster in carrying) {
          val ordered = cluster.pieces.unionsFirst { it }
          // Prima del cambio il vetro toccato mostra il bagliore della pressione (una lastra ferma premuta: la campana
          // attorno al dito, col riempimento di serie).
          val pressed = scene.touch?.takeIf { time < 0.0 && journey == null }
            ?.let { t -> ordered.firstOrNull { it.key == t.key }?.let { piece -> t to piece } }
          val shape = PhysicsPlaneShape(
            slabs = ordered.map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) },
            nominalBlend = spacingPx,
            neck = -1f,
            unions = fluidGlassUnionIndices(ordered),
            resting = true,
            touch = pressed?.let { (t, piece) ->
              PhysicsPlaneTouch(
                t.amount, piece.bounds.left + piece.bounds.width * t.u, piece.bounds.top + piece.bounds.height * t.v, 0f,
              )
            },
          )
          val layers = cluster.pieces.mapNotNull { piece -> contents[piece.key]?.let { layer(scene, piece.key, it) } }
          elements += element("riposo-${restName(cluster)}", shape, scene, null, layers, "riposo: il grappolo tiene il suo vetro")
        }
      }
      if (journey != null && frame != null) {
        for (field in stage) {
          val shape = fluidGlassStagePlane(
            frame = frame, flights = field.flights, origin = Offset.Zero, smoothing = { 0f }, spacingPx = spacingPx,
            neck = driver.neckNow(field.union), swell = if (field.lens) FluidGlassSwell.None else swell,
            density = d, movingMs = driver.movingMs.toFloat(),
            // F3.10: la luce del carattere e quella del dito, come `driveStage`.
            journeyLight = if (field.lens) 0f else driver.journeyLight,
            touch = if (field.lens) null else fluidGlassFingerTouch(
              frame, field.flights, Offset.Zero, driver.finger, driver.fingerGlow, driver.fingerFill,
            ),
          )
          if (shape.slabs.isEmpty()) continue
          val keys = field.flights.mapNotNull { it.targetKey ?: it.sourceKey }
          val layers = keys.mapNotNull { key -> contents[key]?.let { layer(scene, key, it) } }
          val element = element("palco-${field.index}", shape, scene, field.bounds, layers,
            "F3.8a: palco del contenitore (fluidGlassStagePlane), contenuto nel vetro (glassPieceContentLook)")
          element["_collo"] = round3(shape.neck)
          element["_fase"] = round3(driver.phase)
          element["_gonfio"] = round3(if (field.lens) 0f else swell.level)
          // F3.12a: il rigonfiamento in più di ogni pezzo del campo (chi è stato rilasciato arriva gonfio).
          element["_gonfio_pezzi"] = frame.pieces.filter { it.flightId in field.flights }
            .associate { (it.flightId.targetKey ?: it.flightId.sourceKey).toString() to round3(it.swell) }
          element["_luce"] = round3(shape.journeyLight)
          element["_dito"] = round3(shape.touch?.amount ?: 0f)
          element["_riempito"] = round3(shape.touch?.fill ?: 0f)
          element["_unione"] = field.union.name
          element["_contenuto"] = keys.mapNotNull { key ->
            contents[key]?.let { content ->
              linkedMapOf(
                "key" to key.toString(), "parte" to content.role.role.name, "alpha" to round3(content.look.alpha),
                "scala" to round3(content.look.scale), "sigma" to round3(content.look.blur),
              )
            }
          }
          elements += element
        }
      }
      GlassTraceFrame(time, elements)
    }
    val head = linkedMapOf<String, Any?>(
      "_" to "F3.8a: ${scene.note}",
      "palette" to "engine",
      "background" to scene.background,
    )
    val interruptions = scene.steps.drop(1).map { it.atMs }
    if (interruptions.isNotEmpty()) {
      head["events"] = interruptions.map { linkedMapOf("t" to it, "kind" to "interrupt", "label" to "stato nuovo") }
    }
    scene.apple?.let { head["apple"] = it }
    return GlassTrace(
      name = scene.name, phase = scene.phase, density = d, theme = if (scene.dark) "dark" else "light",
      widthDp = scene.widthDp, heightDp = scene.heightDp, fps = Fps, frames = frames, head = head,
    )
  }

  private fun restName(cluster: GlassPlaneCluster): String = when (val key = fluidGlassRestKey(cluster)) {
    is String -> key
    else -> cluster.pieces.joinToString("+") { it.key.toString() }
  }

  /** Lo strato del contenuto di un pezzo: il testo o l'icona al suo posto, con l'aspetto e il ritaglio di adesso. */
  private fun layer(scene: Scene, key: Any, content: Content): Map<String, Any?>? {
    val label = scene.labels[key] ?: return null
    if (!content.shown) return null
    val d = scene.density
    val flight = content.flight
    val look = flight?.look ?: content.look
    if (look.alpha <= 0.001f) return null
    val place = content.place.translate(flight?.offset ?: Offset.Zero)
    val out = linkedMapOf<String, Any?>(
      "name" to "c-$key",
      "x" to round3(place.left / d), "y" to round3(place.top / d),
      "w" to round3(place.width / d), "h" to round3(place.height / d),
      "color" to if (scene.dark) "#F2F2F7" else "#17171A",
    )
    if (label.text != null) {
      out["label"] = label.text
      out["labelSizeDp"] = label.sizeDp
      out["labelWeight"] = 600
    } else {
      out["icon"] = label.icon
      out["iconSizeDp"] = label.sizeDp
    }
    if (look != GlassContentLook.Sharp) {
      val sigmaPx = glassContentSigmaPx(look.blur, GlassContentDefaults.BlurReference.value * d)
      if (sigmaPx > 0.05f) {
        // Il banco legge `blurDp` come il raggio di `BlurEffect`: lo stesso conto del contenitore.
        out["blurDp"] = round3(glassContentBlurRadiusPx(sigmaPx) / d)
        out["marginDp"] = 12
      }
      out["alpha"] = round3(look.alpha)
      out["scale"] = round3(look.scale)
    }
    val clip = flight?.clip
    if (clip != null) {
      // Il ritaglio è nelle coordinate del pezzo: sulla tela è spostato del suo posto (non del viaggio del contenuto).
      val onCanvas = clip.translate(content.place.left, content.place.top)
      out["clip"] = linkedMapOf(
        "x" to round3(onCanvas.left / d), "y" to round3(onCanvas.top / d),
        "w" to round3(onCanvas.width / d), "h" to round3(onCanvas.height / d),
        "radius" to round3(flight.radii.topLeft / d),
      )
    }
    return out
  }

  /** L'elemento `material` di un campo: la forma [shape] (pixel della tela) con la ricetta dei tasti di vetro. */
  private fun element(
    name: String,
    shape: PhysicsPlaneShape,
    scene: Scene,
    stage: Rect?,
    content: List<Map<String, Any?>>,
    note: String,
  ): LinkedHashMap<String, Any?> {
    val d = scene.density
    val plan = PhysicsRenderPlan()
    buildPlanePlan(plan, shape)
    val shortDp = physicsRecipeShortPx(plan) / d
    val recipe = glassRecipe(
      role = GlassRole.Interactive, widthDp = shortDp, heightDp = shortDp, darkSide = scene.dark, optics = ButtonOptics,
      intensity = 1f,
    )
    // Il vetro in viaggio (scelta B), il carattere del viaggio del palco (F3.10), poi la comparsa: l'ordine di
    // `physicsLiquidRecipe` e di `PhysicsLiquidSurface.fill`.
    glassTravelRecipe(recipe, GlassLiquidProfile.Default.travel, shape.motion)
    val journey = if (stage != null) scene.journey else null
    if (journey != null) glassJourneyRecipe(recipe, journey, shape.journeyLight, scene.dark)
    glassMaterializeRecipe(recipe, shape.materialize)
    val padding = glassTravelPaddingDp(GlassRole.Interactive, ButtonOptics, journey = journey)
    // Il bagliore: quello del vetro fermo premuto, o la luce del dito che il palco porta.
    val at = FloatArray(2)
    val glow = physicsLiquidGlow(0f, 1f, null, plan.bounds, shape.touch, recipe, at)
    val inputs = physicsLiquidInputs(
      plan = plan, recipe = recipe, scale = 1f, screenDensity = d,
      paddingPx = floor(max(padding * d, 0f)), glowAmount = glow, touchX = at[0], touchY = at[1],
      out = GlassDrawInputs(GlassFieldMaxPieces), neckPinch = shape.pinch,
    )
    // Il nodo del palco: fin dove arriva l'ombra (o l'alone del carattere), più il rigonfiamento al colmo.
    val reach = (glassTravelShadowReachDp(ButtonOptics, gain = 0f, journey = journey) + 2f + FluidMotion.PlaneSwellMaxDp) * d
    val union = stage ?: plan.bounds
    val box = Rect(union.left - reach, union.top - reach, union.right + reach, union.bottom + reach)
    val element = liquidPhysicsElement(
      name, inputs, Offset.Zero, d, scene.widthDp, scene.heightDp, content, stage = box, note = note,
    )
    if (shape.slabs.size == 1) narrowToOne(element)
    return element
  }

  /** Lo scrittore fisico prepara otto caselle: `Body1` ha gli stessi uniform con una casella (come in F3.1 e F3.3). */
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

  private const val Density = 3f

  private fun piece(
    id: String, x: Float, y: Float, w: Float = 44f, h: Float = 44f,
    transition: GlassPlaneTransition = GlassPlaneTransition.MatchedGeometry,
    union: Any? = null,
  ) = GlassPlanePiece(
    key = id, id = id, bounds = Rect(x * Density, y * Density, (x + w) * Density, (y + h) * Density),
    radii = FluidCornerRadii.all(minOf(w, h) / 2f * Density), transition = transition, unionId = union,
  )

  private fun state(widthDp: Float, heightDp: Float, vararg pieces: GlassPlanePiece) =
    GlassPlaneState(pieces.toList(), PhysicsPlaneFusionDp * Density, Rect(0f, 0f, widthDp * Density, heightDp * Density))

  private val Select = linkedMapOf(
    "name" to "select-done", "video" to "wwdc2025-219_hd", "t0Frame" to 9031, "crop" to listOf(300, 250, 1320, 587),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B2_select_done_f9029-9076.jpg",
    "speed" to 2.34,
    "note" to "R4 B2 (d), classe C rallentata: vale l'ordine delle fasi. t0 = f9031, l'ultimo fotogramma con «Select» " +
      "nitido; riposo a f9066 (35 fotogrammi, 1168 ms): 2,3 volte i 500 ms di una nascita (F3.12a; il render è " +
      "rallentato di 2,3 volte anche rispetto alle clip di Apple a tempo vero).",
  )

  fun scenes(): List<Scene> {
    val w = 240f
    val h = 120f
    fun st(vararg p: GlassPlanePiece) = state(w, h, *p)
    // «Modifica» (un tasto tondo) e «Fatto» (una capsula col testo) a 8 dp: il caso tipo della scheda e della galleria.
    val edit = piece("modifica", 44f, 38f)
    val done = piece("fatto", 96f, 38f, w = 88f)
    val oneTwo = mapOf<Any, Label>("modifica" to Label(icon = "fluid:edit", sizeDp = 22f), "fatto" to Label(text = "Fatto"))
    val out = ArrayList<Scene>()
    for (dark in listOf(false, true)) {
      val theme = if (dark) "scuro" else "chiaro"
      out += Scene(
        "uno-due-$theme", "«Modifica» fa nascere «Fatto»: il testo arriva sfocato dentro il vetro, mai prima", w, h, dark,
        st(edit), listOf(Step(0.0, st(edit, done))), oneTwo, lastFrame = 72, apple = listOf(Select),
      )
    }
    // La clip di Apple «Select → Done»: un tasto con la scritta diventa due tondi e «Fatto», e **tutto** il contenuto
    // cambia. Il vetro di prima se ne va confluendo, i tre nuovi nascono da lui: il vecchio contenuto esce dov'era nei
    // primi fotogrammi, i nuovi arrivano sfocati dentro il vetro dopo metà viaggio. Le proporzioni sono quelle della
    // clip in altezze del tasto: la scritta 2,2 H, i tondi 1 H, i varchi 0,3 H, «Done» 2 H.
    val chooseDp = 264f
    fun chosen(vararg p: GlassPlanePiece) = state(chooseDp, h, *p)
    val choose = piece("seleziona", 83f, 38f, w = 96f)
    val circle = piece("cerchio", 30f, 38f)
    val triangle = piece("triangolo", 87f, 38f)
    val finish = piece("fatto", 144f, 38f, w = 88f)
    val chooseLabels = mapOf<Any, Label>(
      "seleziona" to Label(text = "Seleziona"), "cerchio" to Label(icon = "fluid:search", sizeDp = 22f),
      "triangolo" to Label(icon = "fluid:share", sizeDp = 22f), "fatto" to Label(text = "Fatto"),
    )
    for (dark in listOf(false, true)) {
      val theme = if (dark) "scuro" else "chiaro"
      out += Scene(
        "seleziona-fatto-$theme", "«Seleziona» diventa due tondi e «Fatto», come «Select → Done»: il vecchio esce, i nuovi nascono",
        chooseDp, h, dark, chosen(choose), listOf(Step(0.0, chosen(circle, triangle, finish))), chooseLabels,
        lastFrame = 72, apple = listOf(Select),
      )
    }
    out += Scene(
      "due-uno-chiaro", "«Fatto» confluisce in «Modifica»: il testo si sfoca dov'era e il bordo lo copre", w, h, false,
      st(edit, done), listOf(Step(0.0, st(edit))), oneTwo, lastFrame = 72,
    )
    out += Scene(
      "uno-due-interrotto", "«Fatto» nasce, a 150 ms se ne va, a 300 torna: nessuna scritta salta (F3-f)", w, h, false,
      st(edit), listOf(Step(0.0, st(edit, done)), Step(150.0, st(edit)), Step(300.0, st(edit, done))), oneTwo,
      lastFrame = 108,
    )
    // Due capsule con il loro id: divise a 96 dp, accostate senza varco. Chi resta viaggia nitido col suo vetro.
    val music = piece("musica", 36f, 38f, w = 112f)
    val videoFar = piece("video", 244f, 38f, w = 112f)
    val videoNear = piece("video", 148f, 38f, w = 112f)
    val capsules = mapOf<Any, Label>("musica" to Label(text = "Musica"), "video" to Label(text = "Video"))
    val wideDp = 392f
    fun wide(vararg p: GlassPlanePiece) = state(wideDp, h, *p)
    out += Scene(
      "capsule-fuse", "due capsule si accostano: «Video» viaggia nitido dentro la sua capsula", wideDp, h, false,
      wide(music, videoFar), listOf(Step(0.0, wide(music, videoNear))), capsules, lastFrame = 54,
    )
    out += Scene(
      "capsule-divise", "due capsule si dividono passando per un collo: il testo va con loro", wideDp, h, false,
      wide(music, videoNear), listOf(Step(0.0, wide(music, videoFar))), capsules, lastFrame = 54,
    )
    // «cerca» e «preferiti» fermi a 8 dp, «play» lontano compare per lente e poi scompare.
    val lensDp = 280f
    fun lens(vararg p: GlassPlanePiece) = state(lensDp, h, *p)
    val search = piece("cerca", 40f, 38f)
    val star = piece("preferiti", 92f, 38f)
    val play = piece("play", 196f, 38f, transition = GlassPlaneTransition.Materialize)
    val appear = mapOf<Any, Label>(
      "cerca" to Label(icon = "fluid:search", sizeDp = 22f), "preferiti" to Label(icon = "fluid:star", sizeDp = 22f),
      "play" to Label(icon = "fluid:play_arrow", sizeDp = 22f),
    )
    out += Scene(
      "comparsa", "un tasto lontano compare per lente: i due fermi restano due, il simbolo si condensa senza pause",
      lensDp, h, false, lens(search, star), listOf(Step(0.0, lens(search, star, play))), appear, lastFrame = 60,
    )
    out += Scene(
      "scomparsa", "lo stesso tasto scompare: il simbolo via nei primi 130 ms, poi la lente, poi il contorno",
      lensDp, h, false, lens(search, star, play), listOf(Step(0.0, lens(search, star))), appear, lastFrame = 66,
    )
    // Tre tasti dello stesso gruppo a 8 dp: «Unisci» e «Separa» sono un viaggio del collo, nessun pezzo si muove.
    val union = "sagoma"
    fun block(united: Boolean) = (0..2).map { index ->
      piece("vista-$index", 40f + index * 72f, 38f, w = 64f, union = if (united) union else null)
    }.toTypedArray()
    val views = mapOf<Any, Label>(
      "vista-0" to Label(icon = "fluid:favorite", sizeDp = 22f), "vista-1" to Label(icon = "fluid:mic", sizeDp = 22f),
      "vista-2" to Label(icon = "fluid:share", sizeDp = 22f),
    )
    val blockDp = 296f
    fun blockState(united: Boolean) = state(blockDp, h, *block(united))
    out += Scene(
      "unisci", "tre tasti diventano una sagoma: il collo nasce e cresce, e pieno resta", blockDp, h, false,
      blockState(false), listOf(Step(0.0, blockState(true))), views, lastFrame = 48,
    )
    out += Scene(
      "separa", "la sagoma torna tre tasti: il collo si strozza e si rompe una volta", blockDp, h, false,
      blockState(true), listOf(Step(0.0, blockState(false))), views, lastFrame = 48,
    )
    return out
  }

  /** Il testo sotto il vetro delle tavole «sul testo» (lo stesso delle tracce di F3 e di F3.9). */
  private val Text: Map<String, Any?> = linkedMapOf(
    "kind" to "text", "background" to "#E3E3E5", "color" to "#17171A", "fontSizeDp" to 18, "lineHeightDp" to 26,
    "paddingDp" to 12,
    "text" to "Il piano porta il vetro da uno stato al successivo. Il testo sotto resta fermo. ".repeat(14),
  )

  /** F3.8a, prima dell'aggancio: il rigonfiamento di F3.9 senza la luce. È la colonna «oggi» delle tavole di F3.10. */
  private val BeforeLight: GlassLiquidJourney = GlassLiquidJourneys.Off.copy(swell = GlassLiquidJourneys.Shipped.swell)

  /**
   * Le scene delle tavole di F3.10 (fase `f3-10`): «Seleziona» diventa due tondi e «Fatto», come «Select → Done», con la
   * luce del viaggio agganciata. Per ogni fondo (le dune chiare e scure di F3.8a, e un testo) tre colonne: `oggi` (F3.8a:
   * il rigonfiamento senza la luce), `meta` (la luce «a metà» di serie, senza tocco: un cambio scritto dall'app) e `dito`
   * (la stessa, ma il cambio parte da un tocco su «Seleziona»: il bagliore della pressione riempie il vetro).
   */
  fun f310Scenes(): List<Scene> {
    val h = 120f
    val chooseDp = 264f
    fun chosen(vararg p: GlassPlanePiece) = state(chooseDp, h, *p)
    val choose = piece("seleziona", 83f, 38f, w = 96f)
    val circle = piece("cerchio", 30f, 38f)
    val triangle = piece("triangolo", 87f, 38f)
    val finish = piece("fatto", 144f, 38f, w = 88f)
    val labels = mapOf<Any, Label>(
      "seleziona" to Label(text = "Seleziona"), "cerchio" to Label(icon = "fluid:search", sizeDp = 22f),
      "triangolo" to Label(icon = "fluid:share", sizeDp = 22f), "fatto" to Label(text = "Fatto"),
    )
    val finger = Touch("seleziona", amount = 0.8f, u = 0.42f, v = 0.55f)
    val out = ArrayList<Scene>()
    for ((fund, dark, background) in listOf(
      Triple("chiaro", false, Dunes), Triple("scuro", true, Dunes), Triple("testo", false, Text),
    )) {
      for ((variant, journey, touch) in listOf(
        Triple("oggi", BeforeLight, null),
        Triple("meta", GlassLiquidJourneys.Shipped, null),
        Triple("dito", GlassLiquidJourneys.Shipped, finger),
      )) {
        out += Scene(
          "seleziona-$fund-$variant",
          "F3.10, «Seleziona» diventa tre: $variant (" + when (variant) {
            "oggi" -> "F3.8a, il rigonfiamento senza la luce"
            "meta" -> "la luce a metà, cambio senza tocco"
            else -> "la luce a metà e la luce del dito, cambio da un tocco su «Seleziona»"
          } + ")",
          chooseDp, h, dark, chosen(choose), listOf(Step(0.0, chosen(circle, triangle, finish))), labels,
          lastFrame = 72, background = background, apple = listOf(Select), journey = journey, touch = touch,
          phase = "f3-10",
        )
      }
    }
    return out
  }
}
