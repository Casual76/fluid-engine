package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidFilterPresets
import dev.antigravity.fluidengine.ui.fluid.FluidFilterValue
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTokenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTokenRules
import dev.antigravity.fluidengine.ui.fluid.FluidToken
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassUnionIndices
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneShape
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildPlanePlan
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidGlow
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidInputs
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRecipeShortPx
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.glassMaterializeRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import dev.antigravity.fluidengine.ui.glass.field.glassTravelRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.8b · la traccia `pillola-gettone` del banco del movimento (piano F6B, «pillole»), chiara e scura, a 120 Hz.
 *
 * La ricerca di Mail in basso, aperta, con la fila delle persone sopra il campo (`FluidFilterPresets.tokens`). A 0 ms si
 * tocca «Luca»: il suo vetro vola nel campo e diventa gettone, e il suo posto nella fila si richiude (Marta e Giorgio
 * scorrono a sinistra). A 600 ms il primo «cancella» a testo vuoto sceglie il gettone; a 800 ms il secondo lo toglie: il
 * gettone rifà il viaggio al contrario e torna pillola nel posto che la fila gli riapre.
 *
 * **Le funzioni sono quelle dell'engine**: il viaggio sulla curva del campo ([FluidSearchTokenDefaults.FlightMillis],
 * `CubicBezier(.38, 0, .35, 1)`), il vetro che diventa gettone ([FluidSearchTokenRules.mix]), i posti che si aprono e si
 * chiudono col viaggio (`ToolbarSearchTokenStage.tokenOpening` e `pillOpening`, cioè il progresso del viaggio), il
 * segnaposto che esce ed entra sfocato ([GlassContentJourney], R7) e le misure del gettone. **Approssimato**: le
 * larghezze delle scritte (7,6 dp per lettera a 14 sp, medio); nella resa il vetro in volo svanisce con l'opacità del suo
 * strato (`graphicsLayer`), nel banco con l'intensità del vetro (`materialize`, la stessa curva); il banco non impagina
 * Compose.
 */
internal object F6b8bTokenTraces {
  const val Phase = "f6b-8b"
  const val Density = 2f
  const val Width = 400f
  const val Height = 280f
  private const val Fps = F38aTraces.Fps
  private const val FirstFrame = -6
  const val InAt = 0.0
  const val SelectAt = 600.0
  const val OutAt = 800.0
  private const val LastFrame = 160
  private const val Letter = 7.6f
  private const val Margin = 16f
  private const val Row = 48f
  private const val PillHeight = 48f
  private val Curve = CubicBezierEasing(.38f, 0f, .35f, 1f)
  private val ButtonOptics = GlassDefaults.buttonOptics().sanitized()

  val People = FluidFilterPresets.tokens(listOf(
    FluidFilterValue("anna", "Anna"), FluidFilterValue("luca", "Luca"), FluidFilterValue("marta", "Marta"),
    FluidFilterValue("giorgio", "Giorgio"),
  ))
  private val Luca = FluidToken("luca", "Luca")

  // --- La geometria ------------------------------------------------------------------------------------------------------

  private val rowTop = Height - 24f - Row
  private val cy = rowTop + Row / 2f
  private val BarFieldHeight = dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults.BarFieldHeight.value
  /** Il campo aperto da 48 dp (F6B.8b, `FluidSearchFieldDefaults.BarFieldHeight`), che riempie la riga in basso. */
  val field = Rect(Margin, cy - BarFieldHeight / 2f, Width - Margin, cy + BarFieldHeight / 2f)
  private val pillsTop = rowTop - FluidSearchTokenDefaults.PillsToField.value - PillHeight

  private fun textWidth(label: String) = label.length * Letter
  fun pillWidth(label: String) = maxOf(48f, textWidth(label) + 28f)
  fun tokenWidth(label: String) = maxOf(FluidSearchTokenDefaults.MinWidth.value, textWidth(label) + 2 * FluidSearchTokenDefaults.HorizontalPadding.value)
  val tokenRect = Rect(field.left + 46f, cy - FluidSearchTokenDefaults.Height.value / 2f,
    field.left + 46f + tokenWidth("Luca"), cy + FluidSearchTokenDefaults.Height.value / 2f)

  /** Il progresso del viaggio partito a [at], sulla curva. */
  fun progress(t: Double, at: Double): Float =
    Curve.transform(((t - at) / FluidSearchTokenDefaults.FlightMillis).toFloat().coerceIn(0f, 1f))

  /** Quanto è aperto il posto della pillola di Luca nella fila. */
  fun lucaPillOpening(t: Double): Float = when {
    t < InAt -> 1f
    t < OutAt -> 1f - progress(t, InAt)
    else -> progress(t, OutAt)
  }

  /** Quanto è aperto il posto del gettone di Luca nel campo. */
  fun lucaTokenOpening(t: Double): Float = when {
    t < InAt -> 0f
    t < OutAt -> progress(t, InAt)
    else -> 1f - progress(t, OutAt)
  }

  /** Le pillole della fila a [t]: il riquadro di ognuna dove sta (anche quella di Luca, che il volo nasconde). */
  fun pills(t: Double): List<Pair<String, Rect>> {
    var x = Margin
    return People.filters.map { filter ->
      val w = pillWidth(filter.label)
      val rect = Rect(x, pillsTop, x + w, pillsTop + PillHeight)
      val opening = if (filter.id == "luca") lucaPillOpening(t) else 1f
      x += (w + 8f) * opening
      filter.label to rect
    }
  }

  /** Il volo a [t]: il riquadro, quanto è gettone, verso il campo o no; null fermo. */
  class Flight(val rect: Rect, val mix: Float, val inward: Boolean)

  fun flight(t: Double): Flight? {
    val inward = t >= InAt && t < InAt + FluidSearchTokenDefaults.FlightMillis
    val outward = t >= OutAt && t < OutAt + FluidSearchTokenDefaults.FlightMillis
    if (!inward && !outward) return null
    val p = progress(t, if (inward) InAt else OutAt)
    val pill = pills(t).first { it.first == "Luca" }.second
    val from = if (inward) pills(InAt - 1.0).first { it.first == "Luca" }.second else tokenRect
    val to = if (inward) tokenRect else pill
    return Flight(lerp(from, to, p), FluidSearchTokenRules.mix(p, inward), inward)
  }

  // --- Il contenuto ------------------------------------------------------------------------------------------------------

  private fun ink(dark: Boolean) = if (dark) "#F2F2F7" else "#17171A"
  private fun surface(dark: Boolean) = if (dark) "#000000" else "#FFFFFF"
  private fun accent(dark: Boolean) = if (dark) "#0A84FF" else "#007AFF"

  private fun mixHex(a: String, b: String, f: Float): String {
    fun ch(c: String, j: Int) = c.substring(1 + 2 * j, 3 + 2 * j).toInt(16)
    return "#" + (0 until 3).joinToString("") { j ->
      (ch(a, j) + (ch(b, j) - ch(a, j)) * f.coerceIn(0f, 1f)).roundToInt().toString(16).padStart(2, '0').uppercase()
    }
  }

  private fun text(value: String, x: Float, y: Float, color: String, size: Float = 14f) =
    linkedMapOf<String, Any?>("kind" to "text", "text" to value, "x" to x, "y" to y, "sizeDp" to size, "color" to color)

  private fun capsule(rect: Rect, color: String) = linkedMapOf<String, Any?>("kind" to "rect", "x" to rect.left, "y" to rect.top,
    "w" to rect.width, "h" to rect.height, "radius" to "capsule", "color" to color)

  private fun layer(name: String, items: List<Map<String, Any?>>, alpha: Float = 1f, scale: Float = 1f, blur: Float = 0f): Map<String, Any?>? {
    if (alpha <= 0.001f || items.isEmpty()) return null
    return linkedMapOf("name" to name, "x" to 0f, "y" to 0f, "w" to Width, "h" to Height, "items" to items,
      "alpha" to alpha.coerceIn(0f, 1f), "scale" to scale, "blurDp" to blur, "marginDp" to 12)
  }

  private fun shapeElement(name: String, layers: List<Map<String, Any?>>): Map<String, Any?> = linkedMapOf(
    "kind" to "shape", "name" to name, "x" to 0, "y" to 0, "w" to Width, "h" to Height, "radius" to 0,
    "color" to "#00000000", "contentClip" to "none", "content" to layers,
  )

  private fun blurDp(fraction: Float, sizeDp: Float) = glassContentBlurRadiusPx(glassContentSigmaPx(fraction, sizeDp * Density)) / Density

  // --- Il piano -----------------------------------------------------------------------------------------------------------

  private fun px(r: Rect) = Rect(r.left * Density, r.top * Density, r.right * Density, r.bottom * Density)

  private fun plane(t: Double): GlassPlaneState {
    val flying = flight(t) != null
    val pieces = ArrayList<GlassPlanePiece>()
    pieces += GlassPlanePiece("campo", px(field), FluidCornerRadii.all(px(field).height / 2f), id = "campo")
    for ((label, rect) in pills(t)) {
      val luca = label == "Luca"
      // La pillola di Luca non c'è mentre vola (è il disegno in volo) né mentre è gettone.
      if (luca && (flying || t in InAt..OutAt)) continue
      pieces += GlassPlanePiece("pillola-$label", px(rect), FluidCornerRadii.all(px(rect).height / 2f), id = "pillola-$label")
    }
    return GlassPlaneState(pieces, FluidGlassContainerDefaults.Spacing.value * Density, Rect(0f, 0f, Width * Density, Height * Density))
  }

  /** Il vetro del disegno in volo: un vetro da tasto come quello del chip, con l'intensità [fade]. */
  @Suppress("UNCHECKED_CAST")
  private fun flyingGlass(rect: Rect, fade: Float, dark: Boolean): Map<String, Any?> {
    val d = Density
    val bounds = px(rect)
    val piece = GlassPlanePiece("volo", bounds, FluidCornerRadii.all(bounds.height / 2f), id = "volo")
    val shape = PhysicsPlaneShape(
      slabs = listOf(FluidForm.Slab(bounds, piece.radii, smoothing = ContinuousCornerShape.IosSmoothing)),
      nominalBlend = FluidGlassContainerDefaults.Spacing.value * d,
      neck = -1f,
      unions = fluidGlassUnionIndices(listOf(piece)),
      resting = true,
    )
    val plan = PhysicsRenderPlan()
    buildPlanePlan(plan, shape)
    val shortDp = physicsRecipeShortPx(plan) / d
    val recipe = glassRecipe(role = GlassRole.Interactive, widthDp = shortDp, heightDp = shortDp, darkSide = dark,
      optics = ButtonOptics, intensity = fade.coerceIn(0f, 1f))
    glassTravelRecipe(recipe, GlassLiquidProfile.Default.travel, shape.motion)
    glassMaterializeRecipe(recipe, shape.materialize, soft = shape.softMaterialize)
    val padding = glassTravelPaddingDp(GlassRole.Interactive, ButtonOptics, journey = null)
    val at = FloatArray(2)
    val glow = physicsLiquidGlow(0f, 1f, null, plan.bounds, shape.touch, recipe, at)
    val inputs = physicsLiquidInputs(
      plan = plan, recipe = recipe, scale = 1f, screenDensity = d,
      paddingPx = floor(max(padding * d, 0f)), glowAmount = glow, touchX = at[0], touchY = at[1],
      out = GlassDrawInputs(GlassFieldMaxPieces), neckPinch = shape.pinch,
    )
    val reach = (glassTravelShadowReachDp(ButtonOptics, gain = 0f, journey = null) + 2f + FluidMotion.PlaneSwellMaxDp) * d
    val box = Rect(plan.bounds.left - reach, plan.bounds.top - reach, plan.bounds.right + reach, plan.bounds.bottom + reach)
    val element = liquidPhysicsElement("volo-vetro", inputs, Offset.Zero, d, Width, Height, emptyList(), stage = box,
      note = "F6B.8b: il vetro della pillola in volo, che svanisce entrando nel campo (intensità = 1 − mix)")
    // Un pezzo solo: il corpo da una casella, come `narrowToOne` di F38aTraces.
    val body = element.getValue("body") as MutableMap<String, Any?>
    body["source"] = (body.getValue("source") as String).replace("8ShaderString", "1ShaderString")
    val uniforms = body.getValue("uniforms") as MutableMap<String, Any?>
    listOf("pieceRect", "pieceRadii", "pieceBlend", "pieceSmooth").forEach { name ->
      uniforms[name] = (uniforms.getValue(name) as List<Any?>).take(1)
    }
    uniforms.remove("pieceSoft")
    return element
  }

  // --- La traccia --------------------------------------------------------------------------------------------------------

  fun names(): List<String> = listOf("pillola-gettone", "pillola-gettone-scuro")

  fun build(dark: Boolean): GlassTrace {
    val name = if (dark) "pillola-gettone-scuro" else "pillola-gettone"
    val frames = (FirstFrame..LastFrame).map { glassTraceTimeMs(it, Fps) }
    val background: Map<String, Any?> = linkedMapOf("kind" to "synthetic", "scaleDp" to 64, "detail" to .45,
      "palette" to if (dark) listOf("#16283C", "#5A3145", "#1F423E") else listOf("#E6D0A0", "#AFC9D7", "#F7EEDD"))
    val base = F38aTraces.build(F38aTraces.Scene(name, "F6B.8b: la pillola che diventa gettone nel campo e torna", Width, Height,
      dark, plane(frames.first()), frames.drop(1).map { F38aTraces.Step(it, plane(it)) }, emptyMap(), LastFrame,
      density = Density, firstFrame = FirstFrame, phase = Phase, background = background, instantAt = { true }))
    val color = ink(dark)
    var prompt = GlassContentJourney.rest("prompt")
    var promptState: String? = "prompt"
    val out = base.frames.map { frame ->
      val t = frame.tMs
      val tokens = if (t >= InAt && t < OutAt) listOf(Luca) else emptyList()
      val wantPrompt = if (tokens.isEmpty() && (t < InAt || t >= OutAt)) "prompt" else null
      if (wantPrompt != promptState) {
        prompt = prompt.retarget(t, wantPrompt)
        promptState = wantPrompt
      }
      val flying = flight(t)
      val elements = ArrayList<Map<String, Any?>>(frame.elements)
      val layers = ArrayList<Map<String, Any?>?>()
      // Il campo: la lente, il gettone (fermo), il segnaposto che esce ed entra sfocato.
      layers += layer("lente", listOf(linkedMapOf("kind" to "icon", "icon" to "fluid:search", "x" to field.left + 23f, "y" to cy,
        "sizeDp" to 22f, "color" to color)))
      val tokenOpen = lucaTokenOpening(t)
      val stripShown = t >= InAt && t < OutAt + FluidSearchTokenDefaults.FlightMillis
      val strip = if (stripShown) (tokenWidth("Luca") + FluidSearchTokenDefaults.Gap.value) * tokenOpen +
        (FluidSearchTokenDefaults.TextGap.value - FluidSearchTokenDefaults.Gap.value) else 0f
      if (flying == null && tokens.isNotEmpty()) {
        val selected = t >= SelectAt
        layers += layer("gettone-Luca", listOf(capsule(tokenRect, if (selected) color else accent(dark)),
          text("Luca", tokenRect.center.x - textWidth("Luca") / 2f, cy + 5f, if (selected) surface(dark) else "#FFFFFF")))
      }
      for (ink in prompt.frame(t).layers) {
        if (ink.key == null) continue
        val look = ink.look
        layers += layer("segnaposto-${ink.id}", listOf(text("Cerca nella posta", field.left + 46f + strip, cy + 5.5f, color, 16f)),
          look.alpha, look.scale, blurDp(look.blur, GlassContentDefaults.BlurReference.value))
      }
      // La X nel posto da 44 dp, a 2 dp dal bordo, con l'icona da 26 (i comandi più grandi di F6B.8b).
      layers += layer("chiudi", listOf(linkedMapOf("kind" to "icon", "icon" to "fluid:close", "x" to field.right - 24f, "y" to cy,
        "sizeDp" to 26f, "color" to color)))
      // La fila: le scritte delle pillole ferme (quella di Luca non c'è mentre vola o è gettone).
      for ((label, rect) in pills(t)) {
        if (label == "Luca" && (flying != null || t in InAt..OutAt)) continue
        layers += layer("pillola-$label", listOf(text(label, rect.center.x - textWidth(label) / 2f, rect.center.y + 5f, color)))
      }
      elements += shapeElement("contenuto", layers.filterNotNull())
      // Il disegno in volo: il vetro che svanisce, la tinta che entra, la scritta che cambia inchiostro.
      if (flying != null) {
        elements += flyingGlass(flying.rect, 1f - flying.mix, dark)
        val r = flying.rect
        elements += shapeElement("volo", listOfNotNull(
          layer("volo-tinta", listOf(capsule(r, accent(dark))), alpha = flying.mix),
          layer("volo-scritta", listOf(text("Luca", r.center.x - textWidth("Luca") / 2f, r.center.y + 5f, mixHex(color, "#FFFFFF", flying.mix)))),
        ))
      }
      val marta = pills(t).first { it.first == "Marta" }.second
      elements += linkedMapOf("kind" to "shape", "name" to "misure", "x" to 0, "y" to 0, "w" to 1, "h" to 1,
        "color" to "#00000000", "radius" to 0,
        "voloX" to (flying?.rect?.left ?: -1f), "voloY" to (flying?.rect?.top ?: -1f), "voloW" to (flying?.rect?.width ?: 0f),
        "voloH" to (flying?.rect?.height ?: 0f), "mix" to (flying?.mix ?: -1f), "pillolaLuca" to lucaPillOpening(t),
        "gettoneLuca" to tokenOpen, "martaX" to marta.left)
      GlassTraceFrame(t, elements)
    }
    val head = LinkedHashMap(base.head)
    head["_provenienza"] = "FluidSearchTokenDefaults (viaggio, misure) + FluidSearchTokenRules.mix + i posti che seguono il " +
      "progresso del viaggio (ToolbarSearchTokenStage) + GlassContentJourney per il segnaposto -> F38aTraces (il campo e la " +
      "fila, fermi a ogni fotogramma) + il vetro in volo con l'intensità 1 − mix. Nessun Compose/GPU."
    head["events"] = listOf(
      linkedMapOf("t" to InAt, "kind" to "mark", "label" to "tocco su «Luca»"),
      linkedMapOf("t" to SelectAt, "kind" to "mark", "label" to "cancella: scelto"),
      linkedMapOf("t" to OutAt, "kind" to "mark", "label" to "cancella: tolto"),
    )
    head["probes"] = listOf("voloX", "voloY", "voloW", "voloH", "mix", "pillolaLuca", "gettoneLuca", "martaX").map {
      linkedMapOf("name" to it, "kind" to "value", "path" to "misure.$it")
    }
    return GlassTrace(name, Phase, Density, base.theme, Width, Height, out, Fps, head)
  }
}

class F6b8bTokenTracesTest {
  private fun texts(frame: GlassTraceFrame): List<String> = frame.elements.flatMap { element ->
    @Suppress("UNCHECKED_CAST")
    ((element["content"] as? List<Map<String, Any?>>).orEmpty()).flatMap { layer ->
      ((layer["items"] as? List<Map<String, Any?>>).orEmpty()).mapNotNull { it["text"] as? String }
    }
  }

  private fun measure(frame: GlassTraceFrame, key: String): Float =
    (frame.elements.first { it["name"] == "misure" }[key] as Number).toFloat()

  @Test fun `la pillola vola nel campo e torna, e Luca si legge una volta sola in ogni fotogramma`() {
    val out = File("build/reports/f6b-8b/tracce")
    for (dark in listOf(false, true)) {
      val trace = F6b8bTokenTraces.build(dark)
      trace.write(File(File(out, trace.name), "trace.json"))
      GlassTraceOutput.writeIfRequested(trace)
      assertEquals(120, trace.fps)
      // Mai la pillola e il gettone leggibili insieme: «Luca» è una scritta sola, ferma o in volo.
      for (frame in trace.frames) assertEquals("${trace.name} a ${frame.tMs} ms", 1, texts(frame).count { it == "Luca" })
      val flying = trace.frames.filter { measure(it, "voloW") > 0f }
      assertTrue(flying.isNotEmpty())
      // Parte dalla pillola e arriva al gettone (e al ritorno il contrario), alla curva del campo.
      val first = flying.first()
      val pill = F6b8bTokenTraces.pills(-1.0).first { it.first == "Luca" }.second
      assertEquals(pill.left, measure(first, "voloX"), 1f)
      assertEquals(pill.width, measure(first, "voloW"), 1f)
      val landing = flying.last { it.tMs < F6b8bTokenTraces.OutAt }
      assertEquals(F6b8bTokenTraces.tokenRect.left, measure(landing, "voloX"), 1.5f)
      assertEquals(F6b8bTokenTraces.tokenRect.height, measure(landing, "voloH"), 1.5f)
      // Tutto vetro alla partenza, tutto gettone all'arrivo; mai indietro.
      assertEquals(0f, measure(first, "mix"), 1e-3f)
      assertEquals(1f, measure(landing, "mix"), 0.02f)
      var last = -1f
      for (frame in flying.filter { it.tMs < F6b8bTokenTraces.OutAt }) {
        val m = measure(frame, "mix")
        assertTrue(m >= last - 1e-4f)
        last = m
      }
      // La fila si richiude e si riapre senza salti: Marta scorre al più di pochi dp per fotogramma a 120 Hz.
      for (i in 1 until trace.frames.size) {
        val step = abs(measure(trace.frames[i], "martaX") - measure(trace.frames[i - 1], "martaX"))
        assertTrue("${trace.name} a ${trace.frames[i].tMs} ms: Marta salta di $step dp", step < 6f)
      }
    }
  }
}
