package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildRestPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneFrame
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import kotlin.math.max

/** Le geometrie vengono solo dal piano di produzione. Collo, contenuto e taratura ottica sono di F3.3/4. */
internal object GlassPlaneTraces {
  const val Density = 3f
  const val Fps = 60
  private const val Width = 360f
  private const val Height = 160f
  private val canvas = Rect(0f, 0f, Width * Density, Height * Density)

  internal data class Scenario(
    val name: String,
    val from: GlassPlaneState,
    val to: GlassPlaneState,
    val interruptAtMs: Double? = null,
    val next: GlassPlaneState? = null,
  ) {
    val journey = GlassPlaneJourney(from, to)
    private val interrupted = interruptAtMs?.let { journey.retarget(it, next!!) }
    fun frame(tMs: Double): GlassPlaneFrame = if (interruptAtMs == null || tMs < interruptAtMs) journey.frame(tMs)
      else interrupted!!.frame(tMs - interruptAtMs)
  }

  private fun piece(id: String, x: Float, width: Float = 44f) = GlassPlanePiece(
    key = id, id = id, bounds = Rect(x * Density, 58f * Density, (x + width) * Density, 102f * Density),
    radii = FluidCornerRadii.all(22f * Density),
  )
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(), 12f * Density, canvas)

  fun scenarios(): List<Scenario> {
    val one = state(piece("a", 144f))
    val two = state(piece("a", 112f), piece("b", 196f))
    return listOf(
      Scenario("uno-due", one, two),
      Scenario("divisione", two, one),
      Scenario("contesto-capsule", state(piece("capsula", 120f, 200f)), state(piece("capsula", 232f, 88f))),
      Scenario("comparsa", state(), state(piece("play", 158f))),
      Scenario("uno-due-interrotto", one, two, 150.0, one),
      Scenario("divisione-interrotta", two, one, 150.0, two),
    )
  }

  fun build(scenario: Scenario): GlassTrace {
    // Cinque fotogrammi di riposo prima di t0 e una coda ferma dopo il viaggio, per verificare le consegne.
    val frames = (-5..42).map { index ->
      val time = glassTraceTimeMs(index, Fps)
      val frame = scenario.frame(max(time, 0.0))
      val elements = frame.fields.mapIndexed { fieldIndex, field ->
        val flying = field.flights.map { id -> frame.pieces.single { it.flightId == id } }
        val slabs = flying.map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) }
        val plan = PhysicsRenderPlan()
        // Nessuna curva del collo duplicata: F3.3 riceve parentela e riquadri nel metadato `_piano`.
        val form = if (slabs.size == 1) slabs.single() else FluidForm.Group(slabs, blendRadius = 0f)
        buildRestPlan(plan, form)
        val amount = flying.first().materialize
        require(flying.all { it.materialize == amount }) { "questa traccia richiede materialize per pezzo (F3.3)" }
        val optics = GlassDefaults.buttonOptics()
        val recipe = glassRecipe(
          role = GlassRole.Interactive, widthDp = form.frame.width / Density, heightDp = form.frame.height / Density,
          darkSide = false, optics = optics, intensity = amount,
        )
        val padding = glassTravelPaddingDp(GlassRole.Interactive, optics)
        val inputs = liquidPhysicsInputs(plan, recipe, Density, padding, rest = frame.finished, shadow = true)
        val element = liquidPhysicsElement("piano$fieldIndex", inputs, Offset.Zero, Density, Width, Height,
          stage = field.bounds, note = "F3.1: geometria pura; collo e contenuto demandati a F3.3/F3.4")
        if (field.kernel == GlassBodyKernel.One) narrowToOne(element)
        element["_kernel"] = field.kernel.name
        element["_piano"] = flying.map { piece -> linkedMapOf(
          "key" to piece.piece.key.toString(), "id" to piece.piece.id?.toString(), "change" to piece.change.name,
          "bornFrom" to piece.bornFrom?.toString(), "mergesInto" to piece.mergesInto?.toString(),
          "materialize" to piece.materialize,
          "rectPx" to listOf(piece.bounds.left, piece.bounds.top, piece.bounds.right, piece.bounds.bottom),
          "velocityPxPerSecond" to listOf(piece.velocity.bounds.left, piece.velocity.bounds.top,
            piece.velocity.bounds.right, piece.velocity.bounds.bottom),
        ) }
        element
      }
      GlassTraceFrame(time, elements)
    }
    return GlassTrace(
      name = scenario.name, phase = "f3", density = Density, theme = "light", widthDp = Width, heightDp = Height,
      fps = Fps, frames = frames,
      head = linkedMapOf(
        "_" to "F3.1: piano di produzione e ricetta dell'engine. Non certifica collo, contenuto o passaggio Compose.",
        "background" to linkedMapOf("kind" to "text", "background" to "#E3E3E5", "color" to "#17171A",
          "fontSizeDp" to 18, "lineHeightDp" to 26, "paddingDp" to 12,
          "text" to "Il piano porta il vetro da uno stato al successivo. Il testo sotto resta fermo. " .repeat(10)),
        "events" to if (scenario.interruptAtMs == null) emptyList<Any>() else listOf(
          linkedMapOf("t" to scenario.interruptAtMs, "kind" to "interrupt", "label" to "cambio di stato a metà viaggio")),
      ),
    )
  }

  /** Lo scrittore fisico esistente prepara otto caselle: Body1/Light1 hanno gli stessi uniform con una casella. */
  @Suppress("UNCHECKED_CAST")
  private fun narrowToOne(element: MutableMap<String, Any?>) {
    val layers = listOf(element.getValue("body") as MutableMap<String, Any?>) +
      (element["light"] as? List<MutableMap<String, Any?>>).orEmpty()
    layers.forEach { layer ->
      layer["source"] = (layer.getValue("source") as String).replace("8ShaderString", "1ShaderString")
      val uniforms = layer.getValue("uniforms") as MutableMap<String, Any?>
      listOf("pieceRect", "pieceRadii", "pieceBlend", "pieceSmooth").forEach { name ->
        uniforms[name] = (uniforms.getValue(name) as List<Any?>).take(1)
      }
      uniforms.remove("pieceSoft")
    }
  }
}
