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
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsJourney
import dev.antigravity.fluidengine.ui.fluidphysics.MorphInflationCapDp
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.SlabTransit
import dev.antigravity.fluidengine.ui.fluidphysics.buildRestPlan
import dev.antigravity.fluidengine.ui.fluidphysics.buildTransitPlan

/*
 * Le tracce di F1B.1 (`GLASS_TRACE_DIR`, fase `f1b`): i viaggi col MATERIALE NUOVO.
 *
 *  - `nuovo-menu-apertura`, `nuovo-barra-pagine`: le due tracce «di oggi» di F0.5 **rivestite**. La corsa è la stessa
 *    funzione (`buildMenuTrace`, `buildBarTrace`): stessa geometria, stesse molle, stessi tempi, stesso contenuto;
 *    cambia chi veste il tasto fermo e la superficie fisica ([TraceDress.Liquid]). Il tocco del tasto col materiale
 *    nuovo c'è già: è `liquid-tasto` (`LiquidTasto.kt`, giro di correzione di F1A);
 *  - `tasto-diventa-due`, `gruppo-diventa-menu`: due viaggi d'oro che F0 non aveva. Il motore li sa fare da sempre
 *    con `morphTo` verso un `FluidForm.Group` (e da un gruppo a una lastra): qui sono lo stesso transito
 *    ([SlabTransit], gli accoppiamenti che `morphTo` costruisce: ogni pezzo d'arrivo parte dal pezzo di partenza più
 *    vicino) guidato dalla molla di serie ([FluidPhysicsJourney.Spring]) valutata in forma chiusa, col suo
 *    oltrepasso. Non esiste ancora un componente dell'engine che li dichiari (un tasto che si divide, un gruppo che
 *    apre un menu): è lavoro di F3 e F5A; oggi li scrive l'app con `morphTo`.
 *
 * Senza ombra, come le scene (vedi `LiquidPhysics.kt`).
 */
internal class JourneyBuilt(val trace: GlassTrace, val fusions: List<Float>, val pieces: List<Int>, val rests: List<Boolean>)

internal object F1bTraces {
  const val Phase = "f1b"
  const val MenuName = "nuovo-menu-apertura"
  const val BarName = "nuovo-barra-pagine"
  const val SplitName = "tasto-diventa-due"
  const val GroupMenuName = "gruppo-diventa-menu"

  private const val D = 3f
  private const val Fps = 60
  private const val CanvasWidthDp = 300f
  private const val CanvasHeightDp = 300f

  fun menu(): MenuBuilt = buildMenuTrace(OggiMenuApertura.scene(), TraceDress.Liquid, MenuName, Phase)

  fun bar(): BarBuilt = buildBarTrace(OggiBarraPagine.scene(), TraceDress.Liquid, BarName, Phase)

  private fun px(dp: Float) = dp * D

  private fun circle(leftDp: Float, topDp: Float): FluidForm.Slab =
    FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + 44f), px(topDp + 44f)))

  private fun head(note: String): Map<String, Any?> = linkedMapOf(
    "_" to note,
    "palette" to "engine",
    "background" to linkedMapOf(
      "kind" to "text", "background" to "#E3E3E5", "color" to "#0A0A0A", "fontSizeDp" to 22, "lineHeightDp" to 30,
      "paddingDp" to 12,
      "text" to "Signs are made using neon lights. Light is used to define color, tone, and shape. They are " +
        "irreplaceable, and depend on the people who bend them all over the world. Glass is bent by hand, one " +
        "letter at a time, over a flame that has to stay the same colour.",
    ),
  )

  /** Una tappa: da [from] a [to], con la fusione ereditata dalla tappa prima. */
  private class Leg(val from: FluidForm, val to: FluidForm, val pairs: List<Pair<FluidForm.Slab, FluidForm.Slab>>, val blend: Float)

  private fun slabs(form: FluidForm): List<FluidForm.Slab> = when (form) {
    is FluidForm.Slab -> listOf(form)
    is FluidForm.Group -> form.pieces.map { it as FluidForm.Slab }
    is FluidForm.Poly -> error("una sagoma libera non viaggia in queste tracce")
  }

  /** Gli accoppiamenti di `morphTo`, per i due casi di queste tracce: uno verso molti, molti verso uno. */
  private fun leg(from: FluidForm, to: FluidForm): Leg {
    val a = slabs(from)
    val b = slabs(to)
    val pairs = when {
      a.size == 1 -> b.map { a[0] to it }
      b.size == 1 -> a.map { it to b[0] }
      else -> error("accoppiamento non previsto: ${a.size} verso ${b.size}")
    }
    val blend = maxOf((from as? FluidForm.Group)?.blendRadius ?: 0f, (to as? FluidForm.Group)?.blendRadius ?: 0f)
    return Leg(from, to, pairs, blend)
  }

  private fun journey(
    name: String,
    note: String,
    role: GlassRole,
    opticsFrom: GlassOptics?,
    optics: GlassOptics,
    legs: List<Leg>,
    /** La miscela del materiale lungo una tappa, dato il progresso: zero resta alla partenza, uno arriva. */
    blendOf: (leg: Int, t: Float) -> Float,
  ): JourneyBuilt {
    val material = PhysicsMaterialSource(opticsFrom?.sanitized(), optics.sanitized())
    val dress = LiquidPhysicsDress(role, material, D)
    val plan = PhysicsRenderPlan()
    val frames = ArrayList<GlassTraceFrame>()
    val fusions = ArrayList<Float>()
    val pieces = ArrayList<Int>()
    val rests = ArrayList<Boolean>()
    var k = 0

    fun emit(rest: Boolean, blend: Float) {
      val recipe = dress.recipe(plan, blend, motion = 0f)
      val inputs = liquidPhysicsInputs(plan, recipe, D, dress.paddingDp, rest)
      val element = liquidPhysicsElement("superficie", inputs, Offset.Zero, D, CanvasWidthDp, CanvasHeightDp)
      frames += GlassTraceFrame(glassTraceTimeMs(k, Fps), listOf(element))
      fusions += plan.fusion
      pieces += plan.pieceCount
      rests += rest
      k++
    }

    fun hold(form: FluidForm, blend: Float, count: Int) {
      buildRestPlan(plan, form)
      repeat(count) { emit(rest = true, blend = blend) }
    }

    hold(legs.first().from, blendOf(0, 0f), 6)
    legs.forEachIndexed { index, leg ->
      val transit = SlabTransit(leg.pairs, leg.blend, overshootCapPx = MorphInflationCapDp * D)
      val spring = TodaySpring(FluidPhysicsJourney.Spring, 0f, 1f)
      var f = 1
      while (f < 240) {
        val nanos = glassTraceTimeNanos(f, Fps)
        if (spring.finished(nanos)) break
        val t = spring.value(nanos)
        buildTransitPlan(plan, transit, t)
        emit(rest = false, blend = blendOf(index, t))
        f++
      }
      hold(leg.to, blendOf(index, 1f), 10)
    }

    val trace = GlassTrace(
      name = name,
      phase = Phase,
      density = D,
      theme = "light",
      widthDp = CanvasWidthDp,
      heightDp = CanvasHeightDp,
      frames = frames,
      fps = Fps,
      head = head(note),
    )
    return JourneyBuilt(trace, fusions, pieces, rests)
  }

  /** Un tasto che diventa due, e i due che tornano uno: `morphTo(Group)` e ritorno. */
  fun split(): JourneyBuilt {
    val one = circle(128f, 128f)
    val two = FluidForm.Group(listOf(circle(102f, 128f), circle(154f, 128f)), blendRadius = px(16f))
    val optics = GlassDefaults.buttonOptics()
    return journey(
      name = SplitName,
      note = "Un tasto da 44 dp che diventa due tasti a 8 dp l'uno dall'altro (fusione 16 dp), e ritorno, col materiale " +
        "nuovo: il transito di `morphTo` verso un `FluidForm.Group` con la molla di serie. A riposo i due pezzi sono " +
        "separati; il collo vive solo in viaggio. Senza ombra (F1B.2).",
      role = GlassRole.Interactive,
      opticsFrom = null,
      optics = optics,
      legs = listOf(leg(one, two), leg(two, one)),
      blendOf = { _, _ -> 1f },
    )
  }

  /** Un gruppo di tre tasti che diventa un menu: `morphTo(Slab)` da un `FluidForm.Group`. */
  fun groupToMenu(): JourneyBuilt {
    val group = FluidForm.Group(
      listOf(circle(100f, 228f), circle(152f, 228f), circle(204f, 228f)),
      blendRadius = px(16f),
    )
    val panel = FluidForm.Slab(
      frame = Rect(px(78f), px(28f), px(248f), px(220f)),
      cornerRadii = FluidCornerRadii.all(px(28f)),
      smoothing = ContinuousCornerShape.IosSmoothing,
    )
    return journey(
      name = GroupMenuName,
      note = "Un gruppo di tre tasti da 44 dp che diventa un menu da 170 x 192 dp, col materiale nuovo: il transito di " +
        "`morphTo` da un `FluidForm.Group` a una lastra, molla di serie; l'ottica scivola da quella dei tasti a quella " +
        "del pannello col progresso. Senza ombra (F1B.2) e senza contenuto.",
      role = GlassRole.Modal,
      opticsFrom = GlassDefaults.buttonOptics(),
      optics = GlassDefaults.panelOptics(compact = true),
      legs = listOf(leg(group, panel)),
      blendOf = { _, t -> t.coerceIn(0f, 1f) },
    )
  }
}
