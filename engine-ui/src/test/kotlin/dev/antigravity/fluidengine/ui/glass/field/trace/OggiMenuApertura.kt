package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchorGap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuEdgeMargin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationEdgeMin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationReferenceHeight
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelPadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelWidth
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowGlyph
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowSidePadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSectionGap
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressSprings
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.fluidDropInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLiquidContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuMaterialBlend
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPanelLayout
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPanelWidth
import dev.antigravity.fluidengine.ui.fluid.fluidMenuRowHeight
import dev.antigravity.fluidengine.ui.fluid.fluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.fluidPressedBounds
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluid.solveFluidMenuPanel
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModePoly
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

/**
 * `oggi-menu-apertura`: un'azione di barra con un menu (`FluidBarAction(actions = …)`, presentazione
 * `Expand`) tenuta premuta finche' si apre. E' il menu che **nasce dal suo tasto** dell'engine di oggi:
 * il tasto tondo da 44 dp gonfiato dal dito (×1,5, `ExpandRound`) diventa il pannello, posato **sopra**
 * l'ancora (`FluidMenuPlacement.OverAnchor`), con la goccia, la pelle che passa dal tasto al pannello, la
 * faccia del tasto che se ne va e le voci che arrivano ritagliate dentro il corpo.
 *
 * **Da dove vengono i numeri.** Dell'engine, chiamate come le chiama il codice: le misure e il posto del
 * pannello (`fluidMenuRowHeight`, `fluidMenuPanelLayout`, `fluidMenuPanelWidth`, `solveFluidMenuPanel`), il
 * gonfiore (`fluidDropInflation`), il tasto premuto (`fluidPressTransform`, `fluidPressedBounds`), il viaggio
 * (`FluidMenuJourney.prepare` e `install`, cioe' `driveExternally` con la partenza viva), la sagoma di ogni
 * fotogramma (`FluidPhysicsState.ensurePlan`: lerp, oltrepasso, goccia, piano a riposo), la goccia
 * (`LiquidDrop.step`), le finestre d'opacita' e le mappe del contenuto (`fluidMenuContentAlpha`,
 * `fluidMenuFaceAlpha`, `fluidMenuLiquidContentMap`, `fluidMenuContentMap`, `travelClipIn`), il materiale
 * (`fluidMenuMaterialBlend`, `PhysicsMaterialSource`, `liquidMaterial`, `physicsLensUniforms`).
 *
 * **Ricostruito.** L'orologio: le molle di `FluidMenuJourney.run` (`dropOpen` per il progresso, `snappy`
 * per la pressione che si scioglie) valutate in forma chiusa e scritte nelle `Animatable` del viaggio con
 * `snapTo`; il conducente della goccia (`TodayLiquidDriver`); l'assemblaggio degli uniform
 * (`todayPhysicsElement`); le pellicole del tema (`TodayPalette`); il posto delle voci nella riga (testo a
 * 16 dp con la linea di base a 30 dp dall'alto della riga, icona a destra: e' la riga di
 * `FluidGlassMenuRow`, a occhio sulla tipografia); il ritaglio come riquadro diritto (l'engine ritaglia
 * un rettangolo **arrotondato**: gli angoli del ritaglio non si vedono). Il preambolo (il tasto premuto
 * prima che il menu parta) e' `glassSurface`, vedi `todayButtonElement`.
 *
 * **Scelte di scena.** Il tasto sta in basso a sinistra, come il tasto del telefono di Apple (R4 B1 a): le
 * azioni di barra dell'engine stanno di solito in alto a destra, e li' il pannello si apre verso il basso
 * dallo stesso codice (`OverAnchor`). Il dito tiene premuto 400 ms (`longPressTimeoutMillis` di Android) e
 * non si muove; quattro voci senza sezioni; tema chiaro, accento di serie; densita' 3.
 */
internal object OggiMenuApertura {
  const val Name = "oggi-menu-apertura"

  private const val Density = 3f
  private val HostDp = Size(411f, 891f)

  /** Il tasto a riposo, in dp dello schermo: in basso a sinistra, come quello di Apple. */
  private val AnchorDp = Rect(16f, 820f, 60f, 864f)

  /** Dove sta l'angolo della tela nello schermo, in dp, e quanto e' grande: il ritaglio di Apple a 1,9 px/pt. */
  private val CanvasOriginDp = Offset(-8f, 643f)
  private const val CanvasWidthDp = 300f
  private const val CanvasHeightDp = 253f

  private const val LongPressMs = 400.0
  private const val PreambleFrames = 6
  private const val TailFrames = 3
  private const val MaxFrames = 120

  /** Le voci, con l'icona dell'engine e il disegno di servizio del banco se il set manca. */
  private val Items = listOf(
    "Recents" to "fluid:history|circle",
    "Orders" to "fluid:person|circle",
    "Wishlist" to "fluid:favorite|circle",
    "Tracking" to "fluid:archive|circle",
  )

  /** La faccia del tasto: l'icona della barra, 20 dp, nell'accento (`FluidBarAction`). */
  private const val FaceIcon = "fluid:more_horiz|dots"

  /** Quanto e' larga un'etichetta a 17 sp, a occhio: serve solo a far decidere a `fluidMenuPanelWidth`. */
  private const val LabelDpPerChar = 9f

  /** Dalla cima della riga alla linea di base del testo (17 sp su una riga da 48 dp), a occhio. */
  private const val BaselineInRowDp = 30f

  fun build(): Built {
    val d = Density
    val palette = TodayPalette()
    val host = Size(HostDp.width * d, HostDp.height * d)
    val rest = AnchorDp.scaled(d)
    val origin = Offset(CanvasOriginDp.x * d, CanvasOriginDp.y * d)

    // Le misure come le costruisce `rememberFluidMenuPanelMetrics`, con la riga di `bodyLarge` (17/23 sp).
    val metrics = FluidMenuPanelMetrics(
      widthPx = FluidMenuPanelWidth.value * d,
      rowHeightPx = fluidMenuRowHeight(23.dp).value * d,
      paddingPx = FluidMenuPanelPadding.value * d,
      sectionGapPx = FluidMenuSectionGap.value * d,
      radiusPx = FluidRadius.Group.value * d,
      gapPx = FluidMenuAnchorGap.value * d,
      marginPx = FluidMenuEdgeMargin.value * d,
      edgeMinPx = FluidMenuInflationEdgeMin.value * d,
      refHeightPx = FluidMenuInflationReferenceHeight.value * d,
    )
    val layout = fluidMenuPanelLayout(Items.map { false }, metrics)
    val width = fluidMenuPanelWidth(
      metrics = metrics,
      labelWidthsPx = Items.map { it.first.length * LabelDpPerChar * d },
      hasIcon = true,
      hasCheck = false,
      density = d,
      hostWidthPx = host.width,
    )
    val panel = solveFluidMenuPanel(
      anchor = rest,
      host = host,
      width = width,
      height = layout.height,
      gapPx = metrics.gapPx,
      marginPx = metrics.marginPx,
      placement = FluidMenuPlacement.OverAnchor,
    )
    val inflation = fluidDropInflation(panel.width, host.width, metrics.edgeMinPx)

    // La pressione del tasto: parte al contatto (t = -400 ms) con la molla di LibrePods.
    val press = TodaySpring(FluidPressSprings.ExpandIn, 0f, 1f)
    fun pressAt(tMs: Double): Float = press.value(((tMs + LongPressMs) * 1_000_000.0).toLong())
    fun transformAt(tMs: Double) = fluidPressTransform(
      feedback = FluidPressFeedback.ExpandRound,
      progress = pressAt(tMs),
      lean = Offset.Zero,
      width = rest.width,
      height = rest.height,
      dp = d,
      reducedMotion = false,
    )

    // Il click lungo: la sagoma parte dal tasto COME IL DITO LO STA GONFIANDO (`open(true)` in FluidScreen).
    val anchor = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = fluidPressedBounds(rest, transformAt(0.0))
    }
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
    }
    val journey = FluidMenuJourney()
    journey.prepare(
      anchor = anchor,
      hostOrigin = Offset.Zero,
      panel = panel,
      radius = metrics.radiusPx,
      inflation = inflation,
      seed = null,
      freshStart = true,
    )
    journey.install(physics)
    val progress = TodaySpring(FluidMotion.dropOpen(), 0f, 1f)
    val relax = TodaySpring(FluidMotion.snappy(), journey.anchorRelax.value, 1f)
    val driver = TodayLiquidDriver(physics, d)
    val material = PhysicsMaterialSource(GlassDefaults.buttonOptics().sanitized(), GlassDefaults.panelOptics(compact = true).sanitized())
    // Il pannello e' posato all'intero piu' vicino, e il ritaglio parla le sue coordinate (FluidMenuLayer).
    val panelBox = Rect(panel.left.roundToInt().toFloat(), panel.top.roundToInt().toFloat(), panel.right, panel.bottom)

    val frames = ArrayList<GlassTraceFrame>()
    // Il preambolo: il tasto premuto prima del click lungo, disegnato da glassSurface.
    for (k in -PreambleFrames until 0) {
      val t = glassTraceTimeMs(k)
      val transform = transformAt(t)
      val face = todayIconLayer(
        name = "faccia",
        box = rest.translate(transform.translationX, transform.translationY),
        icon = FaceIcon,
        iconSizeDp = 20f,
        color = palette.primary,
        origin = origin,
        density = d,
        scaleX = transform.scaleX,
        scaleY = transform.scaleY,
      )
      val button = todayButtonElement(
        name = "tasto",
        rest = rest,
        transform = transform,
        pressed = pressAt(t),
        optics = GlassDefaults.buttonOptics(),
        tint = palette.controlOverlay,
        origin = origin,
        density = d,
        canvasWidthDp = CanvasWidthDp,
        canvasHeightDp = CanvasHeightDp,
        content = listOf(face),
      )
      frames += GlassTraceFrame(t, listOf(button))
    }

    var ringFrames = 0
    var tail = 0
    var k = 0
    while (k < MaxFrames) {
      val nanos = glassTraceTimeNanos(k)
      val p = progress.value(nanos)
      runBlocking {
        journey.progress.snapTo(p)
        journey.anchorRelax.snapTo(relax.value(nanos))
      }
      driver.frame(nanos)
      val plan = physics.ensurePlan()
      if (plan.mode == PlanModePoly) ringFrames++

      val blend = fluidMenuMaterialBlend(p)
      val look = todayPhysicsLook(
        plan = plan,
        material = material,
        opticsBlend = blend,
        motionLens = physics.liquid.lens,
        tint = lerp(palette.controlOverlay, palette.modalOverlay, clampGlassUnit(blend)),
        density = d,
      )
      val bounds = physics.contentBoundsNow()
      val content = ArrayList<Map<String, Any?>>()

      // La faccia del tasto: segue la sagoma e se ne va entro il 35% del viaggio.
      val faceMap = fluidMenuContentMap(target = rest, current = bounds)
      content += todayIconLayer(
        name = "faccia",
        box = rest.translate(faceMap.translation.x, faceMap.translation.y),
        icon = FaceIcon,
        iconSizeDp = 20f,
        color = palette.primary,
        origin = origin,
        density = d,
        alpha = fluidMenuFaceAlpha(p),
        scaleX = faceMap.scale,
      )

      // Le voci: attaccate alla sagoma, scalate di poco, ritagliate dentro il corpo. Il banco non ritaglia
      // uno strato sul suo riquadro (TRACE.md, «se serve una maschera, la fa l'effetto»): lo strato e' grande
      // quanto la tela, le voci stanno dove la mappa le porta, e il ritaglio di `travelClipIn` e' un effetto.
      val alpha = fluidMenuContentAlpha(p, closing = false)
      if (alpha > 0f) {
        val map = fluidMenuLiquidContentMap(target = panel, current = bounds, alpha = alpha)
        val clip = physics.travelClipIn(panelBox, p)
        val cx = panelBox.width / 2f
        val cy = panelBox.height / 2f
        // Dal pannello (le sue coordinate, posato all'intero) alla tela: prima la mappa del layer (scala
        // attorno al centro, poi la traslazione), poi il posto del pannello.
        fun mapX(x: Float) = panelBox.left + cx + (x - cx) * map.scale + map.translation.x - origin.x
        fun mapY(y: Float) = panelBox.top + cy + (y - cy) * map.scale + map.translation.y - origin.y
        val items = ArrayList<Map<String, Any?>>()
        Items.forEachIndexed { i, (label, icon) ->
          val top = layout.tops[i]
          items += linkedMapOf(
            "kind" to "text",
            "text" to label,
            "x" to round3(mapX(FluidMenuRowSidePadding.value * d) / d),
            "y" to round3(mapY(top + BaselineInRowDp * d) / d),
            "sizeDp" to round3(17f * map.scale),
            "color" to glassColor(palette.onSurface),
          )
          items += linkedMapOf(
            "kind" to "icon",
            "icon" to icon,
            "x" to round3(mapX(panelBox.width - (FluidMenuRowSidePadding.value + FluidMenuRowGlyph.value / 2f) * d) / d),
            "y" to round3(mapY(top + layout.rowHeightPx / 2f) / d),
            "sizeDp" to round3(FluidMenuRowGlyph.value * map.scale),
            "color" to glassColor(palette.onSurface),
          )
        }
        val layer = linkedMapOf<String, Any?>(
          "name" to "voci",
          "x" to 0,
          "y" to 0,
          "w" to CanvasWidthDp,
          "h" to CanvasHeightDp,
          "items" to items,
          "alpha" to alpha,
        )
        if (clip != null) {
          val clipOnCanvas = clip.translate(Offset(panelBox.left - origin.x, panelBox.top - origin.y))
          layer["effect"] = todayRoundRectMask(clipOnCanvas, d)
        }
        content += layer
      }

      frames += GlassTraceFrame(
        glassTraceTimeMs(k),
        listOf(todayPhysicsElement("menu", plan, look, origin, d, CanvasWidthDp, CanvasHeightDp, content)),
      )
      val settled = progress.finished(nanos) && physics.liquid.quiet
      if (settled) tail++ else tail = 0
      if (tail > TailFrames) break
      k++
    }

    val trace = GlassTrace(
      name = Name,
      phase = "f0",
      density = d,
      theme = "light",
      widthDp = CanvasWidthDp,
      heightDp = CanvasHeightDp,
      frames = frames,
      head = head(panel.scaled(1f / d).translate(-CanvasOriginDp.x, -CanvasOriginDp.y)),
    )
    return Built(trace, panel, anchor.pressedBounds, ringFrames, driver.integratedFrames)
  }

  /** La traccia e i fatti che il test controlla. */
  class Built(
    val trace: GlassTrace,
    val panelPx: Rect,
    val pressedPx: Rect,
    val ringFrames: Int,
    val integratedFrames: Int,
  )

  /** La testa: pagina, confronto con Apple, sonde. [panel] e' il pannello in dp della tela. */
  private fun head(panel: Rect): Map<String, Any?> {
    val midX = round3(panel.center.x)
    val midY = round3(panel.center.y)
    return linkedMapOf(
      "_" to "Engine di OGGI (F0.5): il menu Expand di un'azione di barra, dal click lungo al pannello fermo. " +
        "Numeri dalle funzioni dell'engine, orologio e assemblaggio ricostruiti nel test " +
        "(OggiMenuApertura.kt). Senza bordo, ombra e bagliore: il banco non disegna quelli di oggi.",
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to "#EAEAEA"),
      "page" to listOf(
        photo(19.0, 21.0, 116.0, 167.0, listOf("#E6E6E6", "#DCDCDC", "#2F5D34", "#E9E9E9", "#3F7A45", "#D2D2D2")),
        photo(147.0, 21.0, 114.0, 167.0, listOf("#DEDEDE", "#5B4BA8", "#E6E6E6", "#7C6BD0", "#A8A8A8", "#E0E0E0")),
        photo(274.0, 21.0, 40.0, 167.0, listOf("#D8D2C8", "#C9B9A0", "#E4E0DA", "#B5A58C", "#E9E6E1", "#CFC6B8")),
      ),
      "apple" to linkedMapOf(
        "video" to "wwdc2025-219_hd",
        "t0Frame" to 9148,
        "crop" to listOf(559, 280, 570, 481),
        "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
        "note" to "R4 B1 (a), classe A: il tasto in basso a sinistra diventa il menu. 1,9 px del video per punto.",
      ),
      "events" to listOf(linkedMapOf("t" to 333.333, "kind" to "mark", "label" to "riposo secondo R4 B1 (a)")),
      "probes" to listOf(
        linkedMapOf(
          "name" to "bordo-alto",
          "kind" to "edge",
          "line" to "v:$midX:0:$CanvasHeightDp",
          "which" to "first",
          "percentOf" to round3(panel.height),
          "label" to "il bordo alto del vetro, dai pixel; in % dell'altezza del pannello",
          "ref" to linkedMapOf("overshootPct" to 6, "peakMs" to listOf(100, 167), "restMs" to listOf(330, 370), "source" to "R4 B1 (a), alta"),
        ),
        linkedMapOf(
          "name" to "larghezza",
          "kind" to "width",
          "line" to "h:$midY:0:$CanvasWidthDp",
          "label" to "la larghezza del vetro a meta' pannello, dai pixel",
          "ref" to linkedMapOf("overshootPct" to 3.4, "peakMs" to listOf(100, 167), "restMs" to listOf(330, 370), "source" to "R4 B1 (a), alta"),
        ),
        linkedMapOf(
          "name" to "luce-del-corpo",
          "kind" to "luma",
          "rect" to listOf(round3(panel.left + 40f), round3(panel.top + 60f), 40, 40),
          "label" to "il vetro che passa dal tasto al pannello",
        ),
      ),
    )
  }

  private fun photo(x: Double, y: Double, w: Double, h: Double, colors: List<String>): Map<String, Any?> = linkedMapOf(
    "kind" to "background",
    "x" to x,
    "y" to y,
    "w" to w,
    "h" to h,
    "background" to linkedMapOf("kind" to "synthetic", "scaleDp" to 46, "detail" to 0.5, "palette" to colors),
  )
}

internal fun Rect.scaled(factor: Float): Rect = Rect(left * factor, top * factor, right * factor, bottom * factor)
