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

/*
 * I tasti di vetro della barra ancorata mentre una pagina figlia arriva sopra la radice (un push): un
 * costruttore solo e due scene.
 *
 *  - `oggi-barra-pagine`: la scena del confronto con Apple (R4 B2 b), quella di F0.5, che non cambia: la
 *    radice ha tre azioni (preferito, condividi, aggiungi), la figlia il tasto indietro e un'azione.
 *  - `oggi-barra-pagine-galleria`: **esattamente** il push che S1 ha registrato sul Tab S9 (pagina «Pagine»,
 *    A con «Cerca» → B con indietro, «Condividi» e «Preferito»), per convalidare il banco (F0-a).
 *
 * Durante il movimento una superficie sola di Fluid-physics porta i pezzi dell'una sui pezzi dell'altra, e le
 * icone si incrociano sopra.
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
 * rifrangono insieme alla pagina. Il titolo agganciato. La pagina che entra da destra: la barra e' chrome, non
 * si muove con le pagine, e la tela e' solo la riga dei controlli (con la pagina ferma sotto).
 */

/** Un tasto della barra: la sua lente comincia a [leftDp] dal bordo sinistro della riga. */
internal class BarButton(val name: String, val leftDp: Float, val icon: String, val iconSizeDp: Float)

/**
 * Una scena del push: la riga dei controlli larga [widthDp] che comincia a [rowTopDp] dall'alto della tela,
 * i tasti della radice ([lower], che esce sotto) e della figlia ([upperActions] e [upperBack], che arriva sopra).
 */
internal class BarScene(
  val name: String,
  val fps: Int,
  val density: Float,
  val widthDp: Float,
  val rowTopDp: Float,
  val canvasHeightDp: Float,
  val lower: List<BarButton>,
  val upperActions: List<BarButton>,
  val upperBack: BarButton,
  val preambleFrames: Int,
  val tailFrames: Int,
  val maxFrames: Int,
  /** La testa della traccia, dato il fotogramma in cui la pagina si posa. */
  val head: (settledFrame: Int) -> Map<String, Any?>,
)

internal class BarBuilt(val trace: GlassTrace, val pairs: List<Pair<Rect, Rect>>, val morphs: List<Float>, val settledFrame: Int)

private const val BarLensDp = 44f
private const val BarBlendDp = 4f

internal fun buildBarTrace(scene: BarScene): BarBuilt {
  val d = scene.density
  val fps = scene.fps
  val palette = TodayPalette()
  // La tela parte [rowTopDp] sopra la riga dei controlli: nell'host (la riga) l'origine della tela e' a -rowTop.
  val origin = Offset(0f, -scene.rowTopDp * d)
  fun lens(b: BarButton) = Rect(b.leftDp * d, 0f, (b.leftDp + BarLensDp) * d, BarLensDp * d)
  val widthPx = scene.widthDp * d

  // `FluidBarJourney.recompute` in un push: `from` sono i pezzi della pagina sopra (la figlia, col tasto
  // indietro in coda perche' ce l'ha solo lei), `to` quelli della pagina sotto.
  val from = scene.upperActions.map(::lens) + lens(scene.upperBack)
  val to = scene.lower.map(::lens)
  val pairs = requireNotNull(FluidPageMotion.barPieceJourney(from, to, widthPx)) { "barPieceJourney: troppi pezzi" }
  val blendPx = BarBlendDp * d
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

  fun icon(b: BarButton, alpha: Float) =
    todayIconLayer(b.name, lens(b), b.icon, b.iconSizeDp, palette.primary, origin, d, alpha = alpha)

  fun restButtons(buttons: List<BarButton>): List<Map<String, Any?>> = buttons.map { b ->
    todayButtonElement(
      name = b.name,
      rest = lens(b),
      transform = FluidPressTransform.Identity,
      pressed = 0f,
      optics = GlassDefaults.buttonOptics(),
      tint = palette.controlOverlay,
      origin = origin,
      density = d,
      canvasWidthDp = scene.widthDp,
      canvasHeightDp = scene.canvasHeightDp,
      content = listOf(icon(b, 1f)),
    )
  }

  val frames = ArrayList<GlassTraceFrame>()
  for (k in -scene.preambleFrames until 0) frames += GlassTraceFrame(glassTraceTimeMs(k, fps), restButtons(scene.lower))

  val morphs = ArrayList<Float>()
  var k = 0
  var settledAt = -1
  while (k < scene.maxFrames) {
    val nanos = glassTraceTimeNanos(k, fps)
    if (travel.finished(nanos)) {
      // `settle()`: la pagina e' arrivata, la superficie condivisa se ne va e i tasti si ridisegnano.
      if (settledAt < 0) settledAt = k
      frames += GlassTraceFrame(glassTraceTimeMs(k, fps), restButtons(scene.upperActions + scene.upperBack))
      if (k - settledAt >= scene.tailFrames) break
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
    val content = scene.lower.map { icon(it, FluidPageMotion.barIconAlpha(morph, ofLower = true)) } +
      (listOf(scene.upperBack) + scene.upperActions).map { icon(it, FluidPageMotion.barIconAlpha(morph, ofLower = false)) }
    frames += GlassTraceFrame(
      glassTraceTimeMs(k, fps),
      listOf(todayPhysicsElement("barra", plan, look, origin, d, scene.widthDp, scene.canvasHeightDp, content)),
    )
    k++
  }

  val trace = GlassTrace(
    name = scene.name,
    phase = "f0",
    density = d,
    theme = "light",
    widthDp = scene.widthDp,
    heightDp = scene.canvasHeightDp,
    frames = frames,
    fps = fps,
    head = scene.head(settledAt),
  )
  return BarBuilt(trace, pairs, morphs, settledAt)
}

/**
 * `oggi-barra-pagine`: la scena del confronto con Apple (R4 B2 b), quella di F0.5. Un push dalla radice (tre
 * azioni) a una figlia (indietro + un'azione), riga larga 411 dp a densita' 3, 60 Hz; sotto i tasti un grigio
 * sfumato come quello del render di Apple.
 */
internal object OggiBarraPagine {
  const val Name = "oggi-barra-pagine"

  private const val WidthDp = 411f
  private const val RowTopDp = 16f
  private const val CanvasHeightDp = 76f

  fun scene(): BarScene = BarScene(
    name = Name,
    fps = 60,
    density = 3f,
    widthDp = WidthDp,
    rowTopDp = RowTopDp,
    canvasHeightDp = CanvasHeightDp,
    lower = listOf(
      BarButton("a-preferito", WidthDp - 152f, "fluid:favorite|circle", 20f),
      BarButton("a-condividi", WidthDp - 102f, "fluid:ios_share|share", 20f),
      BarButton("a-aggiungi", WidthDp - 52f, "fluid:add|plus", 20f),
    ),
    upperActions = listOf(BarButton("b-aggiungi", WidthDp - 52f, "fluid:add|plus", 20f)),
    upperBack = BarButton("b-indietro", 8f, "fluid:arrow_back_ios|back", 20f),
    preambleFrames = 3,
    tailFrames = 3,
    maxFrames = 120,
    head = ::head,
  )

  fun build(): BarBuilt = buildBarTrace(scene())

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
        "line" to "h:${RowTopDp + BarLensDp / 2f}:200:$WidthDp",
        "mode" to "light",
        "label" to "quanto vetro c'e' a destra lungo la riga, dai pixel",
        "ref" to linkedMapOf("restMs" to listOf(267, 333), "source" to "R4 B2 (b), media"),
      ),
      linkedMapOf(
        "name" to "indietro",
        "kind" to "width",
        "line" to "h:${RowTopDp + BarLensDp / 2f}:0:100",
        "mode" to "light",
        "label" to "il tasto indietro che nasce dal seme, dai pixel",
      ),
    ),
  )
}

/**
 * `oggi-barra-pagine-galleria`: il push che S1 ha registrato (`S1/video/adesso-b-barra-pagine.mp4`, fotogrammi
 * in `S1/frames/oggi-barra-pagine/`): nella pagina «Pagine» (`sample/…/PagesDemo.kt`) il tocco su «Apri B»
 * porta sopra «Pagina B». Tab S9 in orizzontale, 120 Hz, densita' 2,125.
 *
 * **Dai sorgenti e dalla taglia del tablet.** La riga e' larga quanto lo schermo, 2560 / 2,125 = 1204,7 dp.
 * La radice A ha un'azione sola, «Cerca» (`Search`), con la lente fra W - 52 e W - 8; la figlia B ha il tasto
 * indietro (`FluidBackButton`, lente fra 8 e 52 dp, freccia da 20 dp) e due azioni, «Condividi» (`Share`, fra
 * W - 102 e W - 58) e «Preferito» (`Star`, fra W - 52 e W - 8). Le icone delle azioni sono da 22 dp
 * (`FluidBarAction`), tutte nell'accento.
 *
 * **Letto dai fotogrammi, dichiarato.** La tela e' la striscia di S1, 2560 x 200 px dall'alto dello schermo
 * (94,1 dp); la riga dei controlli comincia sotto la barra di stato, a 64 px = 30,12 dp (vedi `oggi-tasto-galleria`).
 * Il colore della pagina, #EFF0F5. La pagina B che scivola da destra con la sua ombra e i titoli non ci sono.
 */
internal object OggiBarraPagineGalleria {
  const val Name = "oggi-barra-pagine-galleria"

  const val Density = 2.125f
  const val WidthDp = 2560f / Density
  const val RowTopDp = 64f / Density
  const val CanvasHeightDp = 200f / Density

  fun scene(): BarScene = BarScene(
    name = Name,
    fps = 120,
    density = Density,
    widthDp = WidthDp,
    rowTopDp = RowTopDp,
    canvasHeightDp = CanvasHeightDp,
    lower = listOf(BarButton("a-cerca", WidthDp - 52f, "fluid:search|circle", 22f)),
    upperActions = listOf(
      BarButton("b-condividi", WidthDp - 102f, "fluid:share|share", 22f),
      BarButton("b-preferito", WidthDp - 52f, "fluid:star.fill|circle", 22f),
    ),
    upperBack = BarButton("b-indietro", 8f, "fluid:arrow_back_ios|back", 20f),
    preambleFrames = 3,
    tailFrames = 6,
    maxFrames = 240,
    head = ::head,
  )

  fun build(): BarBuilt = buildBarTrace(scene())

  private fun head(settledFrame: Int): Map<String, Any?> = linkedMapOf(
    "_" to "Engine di OGGI, la scena registrata in S1 (F0-a): il push da «Pagine» (Cerca) a «Pagina B» " +
      "(indietro, Condividi, Preferito) sul Tab S9, 120 Hz, densita' 2,125. Geometria dai sorgenti; quota della " +
      "riga e colore della pagina letti dai fotogrammi (OggiBarraPagine.kt). Senza la fascia di vetro della " +
      "barra, la pagina che scivola, bordo, ombra.",
    "palette" to "engine",
    "background" to linkedMapOf("kind" to "color", "color" to "#EFF0F5"),
    "events" to listOf(
      linkedMapOf("t" to round3(glassTraceTimeMs(settledFrame, 120).toFloat()), "kind" to "mark", "label" to "la pagina si posa: tornano i tasti"),
    ),
  )
}
