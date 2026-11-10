package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import dev.antigravity.fluidengine.ui.page.FluidPageTransition

/**
 * `oggi-barra-pagine`: i tasti di vetro della barra ancorata mentre una pagina figlia arriva sopra la
 * radice (un push). La radice ha tre azioni (preferito, condividi, aggiungi), la figlia il tasto indietro e
 * una azione (aggiungi): durante il movimento una superficie sola di Fluid-physics porta i pezzi dell'una su
 * quelli dell'altra, e le icone si incrociano sopra.
 *
 * **Da dove vengono i numeri.** Dell'engine: l'accoppiamento dei pezzi con i semi
 * (`FluidPageMotion.barPieceJourney`), il viaggio fra due gruppi (`FluidPhysicsState.driveExternally` fra
 * forme di sole lastre, con i suoi accoppiamenti e il ponte), la sagoma di ogni fotogramma (`ensurePlan`), la
 * molla della pagina (`FluidPageMotion.page`), la frazione d'uscita (`FluidPageMotion.exitFraction`), il
 * progresso dei pezzi (`FluidPageMotion.barMorph`), le opacita' delle icone (`FluidPageMotion.barIconAlpha`),
 * il materiale (`PhysicsMaterialSource`, `physicsLensUniforms`).
 *
 * **Ricostruito.** Le posizioni delle lenti nella riga dei controlli (alta 44 dp; azioni in fila da 48 dp con
 * 2 dp fra l'una e l'altra e 6 dp dal bordo; il tasto indietro a 6 dp dall'inizio; la lente da 44 dp al centro
 * del suo bersaglio da 48), che sul dispositivo vengono dal layout; `FluidBarJourney.recompute` e il suo
 * `formOf` (privato: capsule, raccordo di 4 dp fra i pezzi); l'orologio della pagina
 * (`FluidPageHostState.animateTravel`, la molla valutata in forma chiusa); il conducente della goccia (per un
 * gruppo non parte: `dropRestSlabNow` e' null). Prima e dopo il movimento i tasti si disegnano da soli
 * (`glassSurface`, vedi `todayButtonElement`), uno per tasto: la consegna fra i tasti e la superficie
 * condivisa e' la cosa da guardare nei primi e negli ultimi fotogrammi.
 *
 * **Fuori dalla traccia.** La fascia di vetro della barra in alto (`FluidTopBarGlass`), che i tasti
 * rifrangono insieme alla pagina: qui sotto i tasti c'e' la pagina sola, un grigio sfumato come quello del
 * render di Apple (R4 B2 b). Il titolo agganciato. La pagina che entra da destra: la barra e' chrome, non si
 * muove con le pagine, e la tela e' solo la riga dei controlli.
 */
internal object OggiBarraPagine {
  const val Name = "oggi-barra-pagine"

  private const val Density = 3f
  private const val WidthDp = 411f

  /** La riga dei controlli (44 dp) sta a 16 dp dall'alto della tela. */
  private const val RowTopDp = 16f
  private const val CanvasHeightDp = 76f

  private const val LensDp = 44f
  private const val PreambleFrames = 3
  private const val TailFrames = 3
  private const val MaxFrames = 120

  private const val BlendDp = 4f

  private class Button(val name: String, val leftDp: Float, val icon: String)

  /** La radice, che esce sotto: tre azioni da destra. */
  private val Lower = listOf(
    Button("a-preferito", WidthDp - 152f, "fluid:favorite|circle"),
    Button("a-condividi", WidthDp - 102f, "fluid:ios_share|share"),
    Button("a-aggiungi", WidthDp - 52f, "fluid:add|plus"),
  )

  /** La figlia, che arriva sopra: il tasto indietro e un'azione. */
  private val UpperActions = listOf(Button("b-aggiungi", WidthDp - 52f, "fluid:add|plus"))
  private val UpperBack = Button("b-indietro", 8f, "fluid:arrow_back_ios|back")

  fun build(): Built {
    val d = Density
    val palette = TodayPalette()
    // La tela parte 16 dp sopra la riga dei controlli: nell'host (la riga) l'origine della tela e' a -16 dp.
    val origin = Offset(0f, -RowTopDp * d)
    fun lens(b: Button) = Rect(b.leftDp * d, 0f, (b.leftDp + LensDp) * d, LensDp * d)
    val widthPx = WidthDp * d

    // `FluidBarJourney.recompute` in un push: `from` sono i pezzi della pagina sopra (la figlia, col tasto
    // indietro in coda perche' ce l'ha solo lei), `to` quelli della pagina sotto.
    val from = UpperActions.map(::lens) + lens(UpperBack)
    val to = Lower.map(::lens)
    val pairs = requireNotNull(FluidPageMotion.barPieceJourney(from, to, widthPx)) { "barPieceJourney: troppi pezzi" }
    val blendPx = BlendDp * d
    fun formOf(rects: List<Rect>): FluidForm {
      val slabs = rects.map { FluidForm.Slab(it, FluidCornerRadii.all(it.minDimension / 2f)) }
      return slabs.singleOrNull() ?: FluidForm.Group(slabs, blendRadius = blendPx)
    }
    val physics = FluidPhysicsState(FluidForm.Slab(Rect(0f, 0f, 1f, 1f))).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
    }
    var morph = 1f
    physics.driveExternally(from = formOf(pairs.map { it.first }), to = formOf(pairs.map { it.second }), progress = { morph })
    val driver = TodayLiquidDriver(physics, d)
    val material = PhysicsMaterialSource(null, GlassDefaults.buttonOptics().sanitized())
    // Push: la pagina sopra entra da fuori (corsa = larghezza) fino a zero, con la molla delle pagine.
    val travel = TodaySpring(FluidPageMotion.page(), widthPx, 0f)

    fun icon(b: Button, alpha: Float) =
      todayIconLayer(b.name, lens(b), b.icon, 20f, palette.primary, origin, d, alpha = alpha)

    fun restButtons(buttons: List<Button>): List<Map<String, Any?>> = buttons.map { b ->
      todayButtonElement(
        name = b.name,
        rest = lens(b),
        transform = FluidPressTransform.Identity,
        pressed = 0f,
        optics = GlassDefaults.buttonOptics(),
        tint = palette.controlOverlay,
        origin = origin,
        density = d,
        canvasWidthDp = WidthDp,
        canvasHeightDp = CanvasHeightDp,
        content = listOf(icon(b, 1f)),
      )
    }

    val frames = ArrayList<GlassTraceFrame>()
    for (k in -PreambleFrames until 0) frames += GlassTraceFrame(glassTraceTimeMs(k), restButtons(Lower))

    val morphs = ArrayList<Float>()
    var k = 0
    var settledAt = -1
    while (k < MaxFrames) {
      val nanos = glassTraceTimeNanos(k)
      if (travel.finished(nanos)) {
        // `settle()`: la pagina e' arrivata, la superficie condivisa se ne va e i tasti si ridisegnano.
        if (settledAt < 0) settledAt = k
        frames += GlassTraceFrame(glassTraceTimeMs(k), restButtons(UpperActions + UpperBack))
        if (k - settledAt >= TailFrames) break
        k++
        continue
      }
      val e = FluidPageMotion.exitFraction(FluidPageTransition.Slide, travel.value(nanos), widthPx)
      morph = FluidPageMotion.barMorph(e)
      morphs += morph
      driver.frame(nanos)
      val plan = physics.ensurePlan()
      val look = todayPhysicsLook(
        plan = plan,
        material = material,
        opticsBlend = 1f,
        motionLens = physics.liquid.lens,
        tint = palette.controlOverlay,
        density = d,
      )
      // Le icone: quelle della radice (sotto) arrivano tardi, quelle della figlia (sopra) se ne vanno presto,
      // in funzione di `m`; in un push `m` va da 1 a 0, quindi si leggono al contrario.
      val content = Lower.map { icon(it, FluidPageMotion.barIconAlpha(morph, ofLower = true)) } +
        (listOf(UpperBack) + UpperActions).map { icon(it, FluidPageMotion.barIconAlpha(morph, ofLower = false)) }
      frames += GlassTraceFrame(
        glassTraceTimeMs(k),
        listOf(todayPhysicsElement("barra", plan, look, origin, d, WidthDp, CanvasHeightDp, content)),
      )
      k++
    }

    val trace = GlassTrace(
      name = Name,
      phase = "f0",
      density = d,
      theme = "light",
      widthDp = WidthDp,
      heightDp = CanvasHeightDp,
      frames = frames,
      head = head(settledAt),
    )
    return Built(trace, pairs, morphs, settledAt)
  }

  class Built(val trace: GlassTrace, val pairs: List<Pair<Rect, Rect>>, val morphs: List<Float>, val settledFrame: Int)

  private fun head(settledFrame: Int): Map<String, Any?> = linkedMapOf(
    "_" to "Engine di OGGI (F0.5): un push dalla radice (tre azioni) a una figlia (indietro + un'azione). " +
      "I tasti a riposo sono glassSurface, uno per tasto; in movimento una superficie di Fluid-physics. " +
      "Numeri dalle funzioni dell'engine, posizioni e orologio ricostruiti nel test (OggiBarraPagine.kt). " +
      "Senza la fascia di vetro della barra, bordo, ombra.",
    "palette" to "engine",
    "background" to linkedMapOf("kind" to "gradient4", "tl" to "#B7BBC3", "tr" to "#656971", "bl" to "#B2B1B6", "br" to "#71757D"),
    "apple" to linkedMapOf(
      "video" to "wwdc2025-219_hd",
      "t0Frame" to 8634,
      "crop" to listOf(338, 590, 1240, 229),
      "collage" to "artifacts/liquid-glass/riferimenti/collage/B2_barra_render_4m48_f8632-8659.jpg",
      "note" to "R4 B2 (b), classe B: la capsula di tre icone si chiude sul +, la lista diventa la ricerca. 3 px del video per punto.",
    ),
    "events" to listOf(
      linkedMapOf("t" to round3(glassTraceTimeMs(settledFrame).toFloat()), "kind" to "mark", "label" to "la pagina si posa: tornano i tasti"),
    ),
    "probes" to listOf(
      linkedMapOf(
        "name" to "vetro-a-destra",
        "kind" to "width",
        "line" to "h:${RowTopDp + LensDp / 2f}:200:$WidthDp",
        "mode" to "light",
        "label" to "quanto vetro c'e' a destra lungo la riga, dai pixel",
        "ref" to linkedMapOf("restMs" to listOf(267, 333), "source" to "R4 B2 (b), media"),
      ),
      linkedMapOf(
        "name" to "indietro",
        "kind" to "width",
        "line" to "h:${RowTopDp + LensDp / 2f}:0:100",
        "mode" to "light",
        "label" to "il tasto indietro che nasce dal seme, dai pixel",
      ),
    ),
  )
}
