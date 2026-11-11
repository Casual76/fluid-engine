package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
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
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowSidePadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSectionGap
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressSprings
import dev.antigravity.fluidengine.ui.fluid.FluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
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
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

/*
 * Il viaggio di un menu `Expand` (un'azione di barra con un menu, `FluidBarAction(actions = …)`, tenuta
 * premuta finche' si apre) come traccia: un costruttore solo e due scene.
 *
 *  - `oggi-menu-apertura`: la scena del confronto con Apple (R4 B1), il tasto in basso a sinistra come
 *    quello del telefono di Apple, a 60 Hz e densita' 3. E' quella di F0.5 e non cambia.
 *  - `oggi-menu-apertura-galleria`: **esattamente** il viaggio che S1 ha registrato sul Tab S9 (la stella
 *    della pagina «Menù liquidi»), a 120 Hz e densita' 2,125, per convalidare il banco (F0-a).
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
 * `snapTo`; il conducente della goccia (`TodayLiquidDriver`), alla cadenza della scena; l'assemblaggio degli
 * uniform (`todayPhysicsElement`); le pellicole del tema (`TodayPalette`); il posto delle voci nella riga
 * (testo a 16 dp con la linea di base a 30 dp dall'alto della riga, icona a destra: la riga di
 * `FluidGlassMenuRow`, a occhio sulla tipografia); la larghezza delle etichette, che sul dispositivo misura
 * `TextMeasurer` e qui e' una stima per carattere (decide solo se il pannello supera i 252 dp di serie).
 * Il preambolo (il tasto premuto prima che il menu parta) e' `glassSurface`, vedi `todayButtonElement`.
 *
 * **Fuori dalla traccia.** Bordo, ombra e bagliore di oggi (il banco non li rende), la banda grigia fra le
 * sezioni del pannello (`drawMenuSeparators`), e la velatura che l'host dei modali stende sulla pagina
 * quando il menu si apre (sul Tab S9 la pagina passa a `0,31 x L + 136` in ~60 ms: misurata in F0-a).
 */

/** Una voce del pannello: etichetta, icona del banco, e i due stati che ne cambiano il colore. */
internal class MenuItemSpec(
  val label: String,
  val icon: String,
  val enabled: Boolean = true,
  val destructive: Boolean = false,
  val startsSection: Boolean = false,
)

/**
 * Una scena del viaggio: dove sta il tasto nello schermo, quanto e' grande lo schermo (l'host dei modali),
 * quale ritaglio dello schermo e' la tela, a che cadenza e densita' gira il conducente, quanto dura il dito.
 */
internal class MenuScene(
  val name: String,
  val fps: Int,
  val density: Float,
  val hostDp: Size,
  /** La lente del tasto a riposo, in dp dello schermo. */
  val anchorDp: Rect,
  /** L'angolo in alto a sinistra della tela, in dp dello schermo. */
  val canvasOriginDp: Offset,
  val canvasWidthDp: Float,
  val canvasHeightDp: Float,
  /** Dal contatto al click lungo, in ms: `t = 0` della traccia e' il click lungo. */
  val longPressMs: Double,
  /** Quanto preambolo prima di `t = 0`, in ms (il contatto e' a `-longPressMs`). */
  val preambleMs: Double,
  val items: List<MenuItemSpec>,
  val faceIcon: String,
  /** Il lato dell'icona del tasto: 22 dp in `FluidBarAction`; la scena di Apple di F0.5 ne usava 20. */
  val faceIconSizeDp: Float,
  val labelDpPerChar: Float,
  val maxMs: Double,
  val tailFrames: Int,
  /** La testa della traccia (pagina, Apple, sonde), dato il pannello in dp della tela. */
  val head: (panelOnCanvasDp: Rect) -> Map<String, Any?>,
  /** Cio' che sta fermo sulla pagina e si disegna prima del menu (il tasto accanto): elementi della traccia. */
  val still: (origin: Offset, palette: TodayPalette) -> List<Map<String, Any?>> = { _, _ -> emptyList() },
)

/** La traccia e i fatti che il test controlla. */
internal class MenuBuilt(
  val trace: GlassTrace,
  val panelPx: Rect,
  val pressedPx: Rect,
  val ringFrames: Int,
  val integratedFrames: Int,
)

/** Dalla cima della riga alla linea di base del testo (17 sp su una riga da 48 dp), a occhio. */
private const val BaselineInRowDp = 30f

/**
 * @param dress chi veste la traccia: il vetro di oggi (F0.5, di serie) o il materiale nuovo (F1B.1). La corsa è
 *   la stessa, riga per riga: cambia l'elemento scritto per il tasto premuto e per la superficie del menu.
 * @param name con [phase], come si chiama la traccia e dove va: di serie quelli di F0.
 */
internal fun buildMenuTrace(
  scene: MenuScene,
  dress: TraceDress = TraceDress.Today,
  name: String = scene.name,
  phase: String = "f0",
): MenuBuilt {
  val d = scene.density
  val fps = scene.fps
  val palette = TodayPalette()
  val host = Size(scene.hostDp.width * d, scene.hostDp.height * d)
  val rest = scene.anchorDp.scaled(d)
  val origin = Offset(scene.canvasOriginDp.x * d, scene.canvasOriginDp.y * d)

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
  val layout = fluidMenuPanelLayout(scene.items.map { it.startsSection }, metrics)
  val width = fluidMenuPanelWidth(
    metrics = metrics,
    labelWidthsPx = scene.items.map { it.label.length * scene.labelDpPerChar * d },
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

  // La pressione del tasto: parte al contatto (t = -longPressMs) con la molla di LibrePods. Prima vale zero.
  val press = TodaySpring(FluidPressSprings.ExpandIn, 0f, 1f)
  fun pressAt(tMs: Double): Float = press.value(((tMs + scene.longPressMs) * 1_000_000.0).toLong())
  fun transformAt(tMs: Double): FluidPressTransform = fluidPressTransform(
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
  // Il menu è una superficie di ruolo `Modal` (`FluidMenuLayer`), che parte con l'ottica del tasto.
  val liquid = LiquidPhysicsDress(GlassRole.Modal, material, d)
  // Il pannello e' posato all'intero piu' vicino, e il ritaglio parla le sue coordinate (FluidMenuLayer).
  val panelBox = Rect(panel.left.roundToInt().toFloat(), panel.top.roundToInt().toFloat(), panel.right, panel.bottom)
  val still = scene.still(origin, palette)

  val frames = ArrayList<GlassTraceFrame>()
  // Il preambolo: il tasto (fermo, poi premuto) prima del click lungo, disegnato da glassSurface.
  val preambleFrames = ceil(scene.preambleMs * fps / 1000.0 - 1e-9).toInt()
  for (k in -preambleFrames until 0) {
    val t = glassTraceTimeMs(k, fps)
    val transform = transformAt(t)
    val face = todayIconLayer(
      name = "faccia",
      box = rest.translate(transform.translationX, transform.translationY),
      icon = scene.faceIcon,
      iconSizeDp = scene.faceIconSizeDp,
      color = palette.primary,
      origin = origin,
      density = d,
      scaleX = transform.scaleX,
      scaleY = transform.scaleY,
    )
    val button = if (dress == TraceDress.Liquid) {
      liquidButtonElement("tasto", rest, transform, pressAt(t), origin, d, listOf(face))
    } else todayButtonElement(
      name = "tasto",
      rest = rest,
      transform = transform,
      pressed = pressAt(t),
      optics = GlassDefaults.buttonOptics(),
      tint = palette.controlOverlay,
      origin = origin,
      density = d,
      canvasWidthDp = scene.canvasWidthDp,
      canvasHeightDp = scene.canvasHeightDp,
      content = listOf(face),
    )
    frames += GlassTraceFrame(t, still + button)
  }

  var ringFrames = 0
  var tail = 0
  var k = 0
  val maxFrames = ceil(scene.maxMs * fps / 1000.0).toInt()
  while (k < maxFrames) {
    val nanos = glassTraceTimeNanos(k, fps)
    val p = progress.value(nanos)
    runBlocking {
      journey.progress.snapTo(p)
      journey.anchorRelax.snapTo(relax.value(nanos))
    }
    driver.frame(nanos)
    val plan = physics.ensurePlan()
    if (plan.mode == PlanModePoly) ringFrames++

    val blend = fluidMenuMaterialBlend(p)
    val look = if (dress == TraceDress.Liquid) null else todayPhysicsLook(
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
      icon = scene.faceIcon,
      iconSizeDp = scene.faceIconSizeDp,
      color = palette.primary,
      origin = origin,
      density = d,
      alpha = fluidMenuFaceAlpha(p),
      scaleX = faceMap.scale,
    )

    // Le voci: attaccate alla sagoma, scalate di poco, ritagliate dentro il corpo (`travelClipIn`). Lo strato
    // e' grande quanto la tela e le voci stanno dove la mappa del layer le porta; il ritaglio e' `clip`, sullo
    // schermo e non scalato con lo strato, come il `graphicsLayer` con la forma di `fluidMenuTravelClip`.
    val alpha = fluidMenuContentAlpha(p, closing = false)
    if (alpha > 0f) {
      val map = fluidMenuLiquidContentMap(target = panel, current = bounds, alpha = alpha)
      val clip = physics.travelClipIn(panelBox, p)
      val cx = panelBox.width / 2f
      val cy = panelBox.height / 2f
      fun mapX(x: Float) = panelBox.left + cx + (x - cx) * map.scale + map.translation.x - origin.x
      fun mapY(y: Float) = panelBox.top + cy + (y - cy) * map.scale + map.translation.y - origin.y
      val items = ArrayList<Map<String, Any?>>()
      scene.items.forEachIndexed { i, item ->
        val top = layout.tops[i]
        val tone = menuItemTone(item, palette)
        items += linkedMapOf(
          "kind" to "text",
          "text" to item.label,
          "x" to round3(mapX(FluidMenuRowSidePadding.value * d) / d),
          "y" to round3(mapY(top + BaselineInRowDp * d) / d),
          "sizeDp" to round3(17f * map.scale),
          "color" to glassColor(tone),
        )
        items += linkedMapOf(
          "kind" to "icon",
          "icon" to item.icon,
          "x" to round3(mapX(panelBox.width - (FluidMenuRowSidePadding.value + OggiMenuGlyphDp / 2f) * d) / d),
          "y" to round3(mapY(top + layout.rowHeightPx / 2f) / d),
          "sizeDp" to round3(OggiMenuGlyphDp * map.scale),
          "color" to glassColor(tone),
        )
      }
      val layer = linkedMapOf<String, Any?>(
        "name" to "voci",
        "x" to 0,
        "y" to 0,
        "w" to scene.canvasWidthDp,
        "h" to scene.canvasHeightDp,
        "items" to items,
        "alpha" to alpha,
      )
      if (clip != null) {
        val onCanvas = clip.translate(Offset(panelBox.left - origin.x, panelBox.top - origin.y))
        layer["clip"] = linkedMapOf(
          "x" to round3(onCanvas.left / d),
          "y" to round3(onCanvas.top / d),
          "w" to round3(onCanvas.width / d),
          "h" to round3(onCanvas.height / d),
          "radius" to listOf(
            onCanvas.topLeftCornerRadius.x,
            onCanvas.topRightCornerRadius.x,
            onCanvas.bottomRightCornerRadius.x,
            onCanvas.bottomLeftCornerRadius.x,
          ).map { round3(it / d) },
        )
      }
      content += layer
    }

    frames += GlassTraceFrame(
      glassTraceTimeMs(k, fps),
      still + if (look == null) {
        liquid.element("menu", physics, plan, blend, origin, scene.canvasWidthDp, scene.canvasHeightDp, content)
      } else {
        todayPhysicsElement("menu", plan, look, origin, d, scene.canvasWidthDp, scene.canvasHeightDp, content)
      },
    )
    val settled = progress.finished(nanos) && physics.liquid.quiet
    if (settled) tail++ else tail = 0
    if (tail > scene.tailFrames) break
    k++
  }

  val panelOnCanvas = panel.scaled(1f / d).translate(-scene.canvasOriginDp.x, -scene.canvasOriginDp.y)
  val trace = GlassTrace(
    name = name,
    phase = phase,
    density = d,
    theme = "light",
    widthDp = scene.canvasWidthDp,
    heightDp = scene.canvasHeightDp,
    frames = frames,
    fps = fps,
    head = scene.head(panelOnCanvas),
  )
  return MenuBuilt(trace, panel, anchor.pressedBounds, ringFrames, driver.integratedFrames)
}

/** Il colore di una voce: `FluidGlassMenuRow` (`error` per le distruttive, il 38% d'opacita' per le spente). */
private fun menuItemTone(item: MenuItemSpec, palette: TodayPalette): Color {
  val base = if (item.destructive) palette.error else palette.onSurface
  return if (item.enabled) base else base.copy(alpha = base.alpha * 0.38f)
}

/**
 * `oggi-menu-apertura`: la scena del confronto con Apple (R4 B1 a). Il tasto sta in basso a sinistra, come il
 * tasto del telefono di Apple: le azioni di barra dell'engine stanno di solito in alto a destra, e li' il
 * pannello si apre verso il basso dallo stesso codice (`OverAnchor`). Il dito tiene premuto 400 ms (il
 * `longPressTimeoutMillis` di serie di Android) e non si muove; quattro voci senza sezioni; tema chiaro,
 * accento di serie; densita' 3, 60 Hz.
 */
/**
 * Il glifo delle righe del menu della 3.1.2 (`FluidMenuRowGlyph`), 20 dp. Da F1A.11a la costante dell'engine è 22
 * (`FluidMetrics.MenuIconSize`, il set di icone nuovo): le tracce «di oggi» sono la registrazione del menu di prima, e
 * leggerla da lì le cambierebbe (`tools/glassbench/traces/` non deve muoversi).
 */
private const val OggiMenuGlyphDp = 20f

internal object OggiMenuApertura {
  const val Name = "oggi-menu-apertura"

  fun scene(): MenuScene = MenuScene(
    name = Name,
    fps = 60,
    density = 3f,
    hostDp = Size(411f, 891f),
    anchorDp = Rect(16f, 820f, 60f, 864f),
    // Il ritaglio di Apple a 1,9 px/pt.
    canvasOriginDp = Offset(-8f, 643f),
    canvasWidthDp = 300f,
    canvasHeightDp = 253f,
    longPressMs = 400.0,
    preambleMs = 100.0,
    items = listOf(
      MenuItemSpec("Recents", "fluid:history|circle"),
      MenuItemSpec("Orders", "fluid:person|circle"),
      MenuItemSpec("Wishlist", "fluid:favorite|circle"),
      MenuItemSpec("Tracking", "fluid:archive|circle"),
    ),
    faceIcon = "fluid:more_horiz|dots",
    faceIconSizeDp = 20f,
    labelDpPerChar = 9f,
    maxMs = 2000.0,
    tailFrames = 3,
    head = ::head,
  )

  fun build(): MenuBuilt = buildMenuTrace(scene())

  /** La testa: pagina, i due confronti con Apple, sonde. [panel] e' il pannello in dp della tela. */
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
      // Due riprese dello stesso gesto (TRACE.md, «Piu' riprese»): il telefono (R4 B1 a) e il menu sulla foto
      // (R4 B1 b). Il ritaglio della seconda ha l'aspetto della tela e prende tutto il menu, che e' alto.
      "apple" to listOf(
        linkedMapOf(
          "name" to "telefono",
          "video" to "wwdc2025-219_hd",
          "t0Frame" to 9148,
          "crop" to listOf(559, 280, 570, 481),
          "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
          "note" to "R4 B1 (a), classe A: il tasto in basso a sinistra diventa il menu. 1,9 px del video per punto.",
        ),
        linkedMapOf(
          "name" to "foto",
          "video" to "wwdc2025-219_hd",
          "t0Frame" to 13046,
          "crop" to listOf(500, 150, 910, 767),
          "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_foto_f13036-13075.jpg",
          "note" to "R4 B1 (b), classe B: la capsula di tre tasti diventa un menu di sette righe sulla foto.",
        ),
      ),
      "events" to listOf(linkedMapOf("t" to 333.333, "kind" to "mark", "label" to "riposo secondo R4 B1 (a)")),
      "probes" to listOf(
        linkedMapOf(
          "name" to "bordo-alto",
          "kind" to "edge",
          "line" to "v:$midX:0:253.0",
          "which" to "first",
          "percentOf" to round3(panel.height),
          "label" to "il bordo alto del vetro, dai pixel; in % dell'altezza del pannello",
          "ref" to linkedMapOf("overshootPct" to 6, "peakMs" to listOf(100, 167), "restMs" to listOf(330, 370), "source" to "R4 B1 (a), alta"),
        ),
        linkedMapOf(
          "name" to "larghezza",
          "kind" to "width",
          "line" to "h:$midY:0:300.0",
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

/**
 * `oggi-menu-apertura-galleria`: il viaggio che S1 ha registrato (`S1/video/adesso-a-menu-stella.mp4`): la
 * stella della striscia «Le azioni della barra» della pagina «Menù liquidi» (`sample/…/catalog/MenusDemo.kt`,
 * `BarSection`), tenuta premuta 900 ms (`input swipe 1900 870 1900 870 900`), Tab S9 in orizzontale.
 *
 * **Dai sorgenti e dalla taglia del tablet.** Schermo 2560 x 1600 px a densita' 2,125, cioe' 1204,7 x 752,9 dp,
 * ed e' l'host dei modali (quello della radice di `Gallery`). La colonna di `FluidScreen` e' larga 760 dp
 * (`ContentMaxWidth`) e centrata: la striscia (`Stage(height = 96.dp)`) va da 222,35 a 982,35 dp. Le due
 * azioni stanno in una `Row` in alto a destra con 20 dp sopra, 12 dp a destra e 4 dp fra loro, bersagli da
 * 48 dp con la lente da 44 al centro: la stella ha la lente fra 872,35 e 916,35 dp, i tre puntini fra 924,35 e
 * 968,35. Le voci sono `demoActions`: quattro, l'ultima apre una sezione (pannello alto 212 dp), una spenta e
 * una distruttiva.
 *
 * **Letto dai fotogrammi, dichiarato.** La quota della striscia, che dipende dallo scorrimento della pagina:
 * il suo bordo alto e' a 774,5 px (364,47 dp), quindi la lente della stella fra 386,47 e 430,47 dp (misurata
 * a 823–913 px: torna entro 2 px). Il click lungo dopo **500 ms** dal contatto: il primo fotogramma premuto e'
 * a 1435,5 ms nel video e il primo del menu a 1943,8 ms, con un fotogramma lungo prima (1909,9 ms): il ritardo
 * sta fra 474 e 517 ms, e 500 e' il valore del tablet (quello di serie di Android, 400, non torna). La
 * larghezza del pannello: misurata 536 px, cioe' i 252 dp di serie (la stima per carattere e' tenuta sotto).
 * I colori della pagina (#EDEEF1, la lista #F7F9F9 dal suo bordo a 536 dp, il divisore a 608 dp) e il
 * gradiente della striscia, che e' quello di `Stage` (quattro colori fino a 900 px) reso da
 * `artifacts/liquid-glass/moto/f0/strumenti/palco.py`.
 */
internal object OggiMenuAperturaGalleria {
  const val Name = "oggi-menu-apertura-galleria"

  /** Il tablet in orizzontale: 2560 x 1600 px a 2,125 (S1.md, «Ambiente»). */
  const val Density = 2.125f
  val HostDp = Size(2560f / Density, 1600f / Density)

  /** La striscia: colonna di 760 dp centrata (sorgenti), bordo alto letto dai fotogrammi. */
  const val StageLeftDp = 222.35294f
  const val StageRightDp = 982.35294f
  const val StageTopDp = 364.47058f
  const val StageHeightDp = 96f

  /** Le lenti delle due azioni: `Row(TopEnd, padding(top = 20, end = 12), spacedBy(4))`, bersagli da 48. */
  val StarDp = Rect(StageRightDp - 12f - 48f - 4f - 48f + 2f, StageTopDp + 22f, StageRightDp - 12f - 48f - 4f - 2f, StageTopDp + 66f)
  val DotsDp = Rect(StageRightDp - 12f - 48f + 2f, StageTopDp + 22f, StageRightDp - 12f - 2f, StageTopDp + 66f)

  val CanvasOriginDp = Offset(740f, 350f)
  const val CanvasWidthDp = 320f
  const val CanvasHeightDp = 280f

  /** Il palco reso da `palco.py`: la parte della striscia che cade nella tela. */
  const val StagePng = "artifacts/liquid-glass/moto/f0/strumenti/palco-menu-galleria.png"

  fun scene(): MenuScene = MenuScene(
    name = Name,
    fps = 120,
    density = Density,
    hostDp = HostDp,
    anchorDp = StarDp,
    canvasOriginDp = CanvasOriginDp,
    canvasWidthDp = CanvasWidthDp,
    canvasHeightDp = CanvasHeightDp,
    longPressMs = 500.0,
    // Tutto il dito, e due fotogrammi fermi prima del contatto: il primo movimento si vede nella traccia.
    preambleMs = 500.0 + 2 * 1000.0 / 120,
    items = listOf(
      MenuItemSpec("Condividi", "fluid:share|share"),
      MenuItemSpec("Aggiungi ai preferiti", "fluid:star.fill|circle"),
      MenuItemSpec("Modifica (spenta)", "fluid:edit|circle", enabled = false),
      MenuItemSpec("Elimina", "fluid:delete|close", destructive = true, startsSection = true),
    ),
    faceIcon = "fluid:star.fill|circle",
    faceIconSizeDp = 22f,
    labelDpPerChar = 8f,
    maxMs = 1500.0,
    tailFrames = 6,
    head = ::head,
    still = { origin, palette ->
      val dots = DotsDp.scaled(Density)
      listOf(
        todayButtonElement(
          name = "puntini",
          rest = dots,
          transform = FluidPressTransform.Identity,
          pressed = 0f,
          optics = GlassDefaults.buttonOptics(),
          tint = palette.controlOverlay,
          origin = origin,
          density = Density,
          canvasWidthDp = CanvasWidthDp,
          canvasHeightDp = CanvasHeightDp,
          content = listOf(todayIconLayer("icona", dots, "fluid:more_vert|dots", 22f, palette.primary, origin, Density)),
        ),
      )
    },
  )

  fun build(): MenuBuilt = buildMenuTrace(scene())

  private fun head(@Suppress("UNUSED_PARAMETER") panel: Rect): Map<String, Any?> {
    val ox = CanvasOriginDp.x
    val oy = CanvasOriginDp.y
    val stageLeft = maxOf(StageLeftDp, ox) - ox
    return linkedMapOf(
      "_" to "Engine di OGGI, la scena registrata in S1 (F0-a): la stella di «Menù liquidi» sul Tab S9, 120 Hz, " +
        "densita' 2,125. Geometria dai sorgenti della galleria; quota della striscia, click lungo (500 ms) e " +
        "colori della pagina letti dai fotogrammi (OggiMenuApertura.kt). Senza bordo, ombra, bagliore, banda " +
        "fra le sezioni e la velatura della pagina.",
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to "#EDEEF1"),
      "page" to listOf(
        linkedMapOf(
          "kind" to "background",
          "x" to round3(stageLeft),
          "y" to round3(StageTopDp - oy),
          "w" to round3(StageRightDp - ox - stageLeft),
          "h" to StageHeightDp,
          // A sinistra la striscia continua oltre la tela: gli angoli tondi sono solo a destra.
          "radius" to listOf(0, 22, 22, 0),
          "background" to linkedMapOf("kind" to "image", "path" to StagePng),
        ),
        linkedMapOf(
          "kind" to "rect",
          "x" to round3(stageLeft),
          "y" to round3(536f - oy),
          "w" to round3(StageRightDp - ox - stageLeft),
          "h" to round3(CanvasHeightDp - (536f - oy) + 20f),
          "color" to "#F7F9F9",
          "radius" to listOf(0, 22, 0, 0),
        ),
        linkedMapOf("kind" to "rect", "x" to round3(stageLeft), "y" to round3(608f - oy), "w" to round3(StageRightDp - ox - stageLeft), "h" to 0.47, "color" to "#E0E1E2"),
      ),
    )
  }
}

internal fun Rect.scaled(factor: Float): Rect = Rect(left * factor, top * factor, right * factor, bottom * factor)
