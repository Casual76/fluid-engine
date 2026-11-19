package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchorGap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuCellKind
import dev.antigravity.fluidengine.ui.fluid.FluidMenuEdgeMargin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuElementSize
import dev.antigravity.fluidengine.ui.fluid.FluidMenuEntry
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationEdgeMin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationReferenceHeight
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLevelClipCeiling
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLevelPeakBlurDp
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelPadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelWidth
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowGap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowGlyph
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowSidePadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSectionGap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuStructure
import dev.antigravity.fluidengine.ui.fluid.FluidMenuStructureMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidMenuEntries
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLevelEnd
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelEnterAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelEnterBlur
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelEnterScale
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelExitAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelExitBlur
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelExitScale
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLevelWidth
import dev.antigravity.fluidengine.ui.fluid.fluidMenuRowHeight
import dev.antigravity.fluidengine.ui.fluid.fluidMenuStructure
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSubmenuEntries
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSubmenuPanel
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSubtitleRowHeight
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluid.solveFluidMenuPanel
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModePoly
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Le tracce di F5A.3 (la struttura dei menu) per il banco del movimento.
 *
 *  - `sottomenu-apre`, `sottomenu-torna`: un menu dell'host gia' aperto e fermo (un'azione di barra in alto a destra,
 *    che si apre verso il basso) che diventa il sottomenu di «Ordina» e torna indietro. Il vetro passa dal pannello di
 *    prima al nuovo con `driveExternally` e la molla d'andata del menu, come `FluidMenuLevels` nell'host; la goccia la
 *    conduce `TodayLiquidDriver`; le voci sono due strati, quello che se ne va («uscenti») e quello che arriva («voci»),
 *    con le curve del cambio di contenuto (`fluidMenuLevelExit…`, `fluidMenuLevelEnter…`) e il ritaglio sulla sagoma
 *    (`travelClipIn`, a molla sotto l'1).
 *  - `priority-su`, `fixed-su`: l'apertura dal tasto in basso a sinistra di `OggiMenuApertura`, con le voci nell'ordine
 *    che `fluidMenuEntries` da' a un menu `Priority` (capovolto: la prima voce vicino al dito) e a uno `Fixed`.
 *
 * Dell'engine: le celle e le quote (`fluidMenuEntries`, `fluidMenuSubmenuEntries`, `fluidMenuStructure`), la
 * larghezza (`fluidMenuLevelWidth`), il posto dei due pannelli (`solveFluidMenuPanel`, `fluidMenuSubmenuPanel`), le
 * curve del cambio. Ricostruiti: l'orologio (la molla in forma chiusa, il cambio di contenuto lineare in 300 ms), la
 * larghezza delle etichette (una stima per carattere), il posto del testo nella riga (linea di base a 30 dp; con il
 * sottotitolo a 27 e 48 dp), e il gallone dell'intestazione disegnato col simbolo «espandi» invece del gallone girato.
 * Fuori: separatori e bande (il banco non li disegna), la goccia della scelta, lo scrim.
 */
internal object F5a3MenuStructureTraces {
  const val Phase = "f5a-3"
  const val Density = 1.5f
  const val Fps = 60
  val HostDp = Size(411f, 891f)

  /** Un'azione di barra in alto a destra: il menu le si apre sopra, verso il basso. */
  val AnchorDp = Rect(351f, 52f, 395f, 96f)
  val CanvasOriginDp = Offset(111f, 36f)
  const val CanvasWidthDp = 300f
  const val CanvasHeightDp = 340f
  const val PreambleMs = 100.0
  const val MaxMs = 1200.0
  const val TailFrames = 4

  /** Le voci del menu di primo livello: «Ordina» apre il sottomenu e dice la scelta di adesso nel sottotitolo. */
  fun rootActions(): List<FluidContextAction> = listOf(
    FluidContextAction(label = "Condividi", icon = null) {},
    FluidContextAction(label = "Preferiti") {},
    FluidContextAction(
      label = "Ordina",
      subtitle = "Per data",
      children = listOf(
        FluidContextAction(label = "Per data", selected = true) {},
        FluidContextAction(label = "Per nome") {},
        FluidContextAction(label = "Per dimensione") {},
        FluidContextAction(label = "Per tipo") {},
        FluidContextAction(label = "Crescente", startsSection = true, selected = true) {},
      ),
    ) {},
    FluidContextAction(label = "Elimina", destructive = true, startsSection = true) {},
  )

  /** Il simbolo del banco per ogni voce, e quello del gallone. */
  private val Icons = mapOf(
    "Condividi" to "fluid:share|share",
    "Preferiti" to "fluid:star.fill|circle",
    "Elimina" to "fluid:delete|close",
  )
  private const val Chevron = "fluid:chevron_right|circle"
  private const val ChevronOpen = "fluid:expand_more|circle"
  private const val Check = "fluid:check|circle"

  class Built(
    val trace: GlassTrace,
    val from: Rect,
    val to: Rect,
    val integratedFrames: Int,
    val ringFrames: Int,
  )

  private fun panelMetrics(d: Float) = FluidMenuPanelMetrics(
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

  private fun structureMetrics(m: FluidMenuPanelMetrics, d: Float) = FluidMenuStructureMetrics(
    rowHeightPx = m.rowHeightPx,
    subtitleRowHeightPx = fluidMenuSubtitleRowHeight(23.dp, 21.dp).value * d,
    tileHeightPx = m.rowHeightPx,
    paddingPx = m.paddingPx,
    sectionGapPx = m.sectionGapPx,
  )

  /** Un livello: le celle e il pannello (pixel dell'host). */
  private class Level(val structure: FluidMenuStructure, val panel: Rect)

  private fun levels(d: Float): Pair<Level, Level> {
    val host = Size(HostDp.width * d, HostDp.height * d)
    val m = panelMetrics(d)
    val sm = structureMetrics(m, d)
    val label: (String) -> Float = { it.length * 8.5f * d }
    val subtitle: (String) -> Float = { it.length * 7.5f * d }
    val actions = rootActions()
    val rootCells = fluidMenuStructure(fluidMenuEntries(actions, FluidMenuElementSize.Large, reversed = false), sm)
    val rootWidth = fluidMenuLevelWidth(rootCells.entries, m, label, subtitle, d, host.width)
    val anchor = AnchorDp.scaled(d)
    val rootPanel = solveFluidMenuPanel(anchor, host, rootWidth, rootCells.height, m.gapPx, m.marginPx, FluidMenuPlacement.OverAnchor)
    val subCells = fluidMenuStructure(fluidMenuSubmenuEntries(actions[2], reversed = false), sm)
    val subWidth = fluidMenuLevelWidth(subCells.entries, m, label, subtitle, d, host.width)
    val subPanel = fluidMenuSubmenuPanel(rootPanel, subWidth, subCells.height, host, m.marginPx, upward = false)
    return Level(rootCells, rootPanel) to Level(subCells, subPanel)
  }

  /**
   * Il cambio di livello: [opening] dal menu di primo livello al sottomenu, altrimenti il ritorno. Il menu e' fermo
   * per [PreambleMs] prima del tocco (t = 0).
   */
  fun build(name: String, opening: Boolean): Built {
    val d = Density
    val palette = TodayPalette(dark = false)
    val origin = Offset(CanvasOriginDp.x * d, CanvasOriginDp.y * d)
    val (root, sub) = levels(d)
    val from = if (opening) root else sub
    val to = if (opening) sub else root
    val radius = FluidRadius.Group.value * d

    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
    }
    val morph = TodaySpring(FluidMotion.dropOpen(), 0f, 1f)
    var morphValue = 0f
    // Come `FluidMenuLevels.change`: dal pannello del livello di adesso a quello nuovo, a molla.
    physics.driveExternally(
      from = FluidForm.Slab(from.panel, FluidCornerRadii.all(radius)),
      to = FluidForm.Slab(to.panel, FluidCornerRadii.all(radius)),
      progress = { morphValue },
    )
    val driver = TodayLiquidDriver(physics, d)
    // Il menu e' gia' il pannello: il materiale e' quello del pannello (la partenza dell'ottica non conta piu').
    val material = PhysicsMaterialSource(GlassDefaults.panelOptics(compact = true).sanitized(), GlassDefaults.panelOptics(compact = true).sanitized())
    val liquid = LiquidPhysicsDress(GlassRole.Modal, material, d, darkSide = false, shadow = true)

    val frames = ArrayList<GlassTraceFrame>()
    var ringFrames = 0
    var tail = 0
    val preamble = ceil(PreambleMs * Fps / 1000.0 - 1e-9).toInt()
    val maxFrames = ceil(MaxMs * Fps / 1000.0).toInt()
    var k = -preamble
    while (k < maxFrames) {
      val tMs = glassTraceTimeMs(k, Fps)
      val nanos = if (k < 0) 0L else glassTraceTimeNanos(k, Fps)
      morphValue = morph.value(nanos)
      // Il conducente della goccia gira sul tempo vero del fotogramma, anche nel preambolo (fermo).
      driver.frame(glassTraceTimeNanos(k + preamble, Fps))
      val plan = physics.ensurePlan()
      if (plan.mode == PlanModePoly) ringFrames++

      // Come `FluidMenuLevels`: il tempo del contenuto in frazioni di T, fino a 1,15; il pannello sta sul livello nuovo
      // da subito, e il livello di prima resta sotto finche' svanisce.
      val changing = k >= 0
      val u = if (changing) (tMs / GlassContentDefaults.DurationMs).toFloat().coerceIn(0f, FluidMenuLevelEnd) else 0f
      val swapping = changing && u < FluidMenuLevelEnd
      val morphing = changing && !morph.finished(nanos)
      val current = if (changing) to else from
      val panel = current.panel
      val box = Rect(panel.left.roundToInt().toFloat(), panel.top.roundToInt().toFloat(), panel.right, panel.bottom)
      val clip = physics.travelClipIn(box, if (morphing) morphValue.coerceAtMost(FluidMenuLevelClipCeiling) else 1f)
      val clipData = clip?.let {
        val onCanvas = it.translate(Offset(box.left - origin.x, box.top - origin.y))
        linkedMapOf(
          "x" to round3(onCanvas.left / d),
          "y" to round3(onCanvas.top / d),
          "w" to round3(onCanvas.width / d),
          "h" to round3(onCanvas.height / d),
          "radius" to listOf(
            onCanvas.topLeftCornerRadius.x,
            onCanvas.topRightCornerRadius.x,
            onCanvas.bottomRightCornerRadius.x,
            onCanvas.bottomLeftCornerRadius.x,
          ).map { r -> round3(r / d) },
        )
      }
      fun layer(name: String, level: Level, alpha: Float, scale: Float, blurDp: Float): Map<String, Any?> {
        val p = level.panel
        val b = Rect(p.left.roundToInt().toFloat(), p.top.roundToInt().toFloat(), p.right, p.bottom)
        val out = linkedMapOf<String, Any?>(
          "name" to name,
          "x" to 0,
          "y" to 0,
          "w" to CanvasWidthDp,
          "h" to CanvasHeightDp,
          "items" to items(level.structure, b, scale, origin, d, palette),
          "alpha" to alpha,
        )
        if (blurDp > 0f) out["blurDp"] = round3(blurDp)
        // Il ritaglio del pannello vale per tutti e due gli strati: le voci restano dentro il vetro.
        if (clipData != null) out["clip"] = clipData
        return out
      }

      val content = ArrayList<Map<String, Any?>>()
      if (swapping && u < GlassContentDefaults.ExitEnd) {
        content += layer(
          "uscenti", from,
          alpha = fluidMenuLevelExitAlpha(u, blur = true),
          scale = fluidMenuLevelExitScale(u),
          blurDp = fluidMenuLevelExitBlur(u) * FluidMenuLevelPeakBlurDp,
        )
      }
      content += layer(
        "voci", current,
        alpha = if (swapping) fluidMenuLevelEnterAlpha(u, blur = true) else 1f,
        scale = if (swapping) fluidMenuLevelEnterScale(u) else 1f,
        blurDp = if (swapping) fluidMenuLevelEnterBlur(u) * FluidMenuLevelPeakBlurDp else 0f,
      )
      frames += GlassTraceFrame(
        tMs,
        listOf(liquid.element("menu", physics, plan, 1f, origin, CanvasWidthDp, CanvasHeightDp, content, timeMs = tMs)),
      )
      val settled = changing && !swapping && morph.finished(nanos) && physics.liquid.quiet
      if (settled) tail++ else tail = 0
      if (tail > TailFrames) break
      k++
    }

    val trace = GlassTrace(
      name = name,
      phase = Phase,
      density = d,
      theme = "light",
      widthDp = CanvasWidthDp,
      heightDp = CanvasHeightDp,
      frames = frames,
      fps = Fps,
      head = head(from.panel, to.panel, opening),
    )
    return Built(trace, from.panel, to.panel, driver.integratedFrames, ringFrames)
  }

  /** Le voci di un livello posate nel pannello [box], scalate attorno al suo centro come il `graphicsLayer`. */
  private fun items(
    structure: FluidMenuStructure,
    box: Rect,
    scale: Float,
    origin: Offset,
    d: Float,
    palette: TodayPalette,
  ): List<Map<String, Any?>> {
    val cx = box.width / 2f
    val cy = box.height / 2f
    fun mapX(x: Float) = box.left + cx + (x - cx) * scale - origin.x
    fun mapY(y: Float) = box.top + cy + (y - cy) * scale - origin.y
    val out = ArrayList<Map<String, Any?>>()
    for (i in 0 until structure.size) {
      val e = structure.entries[i]
      val top = structure.tops[i]
      val h = structure.heights[i]
      val tone = tone(e, palette)
      val sub = e.action.subtitle?.takeIf { e.kind == FluidMenuCellKind.Row }
      fun text(text: String, baselineDp: Float, sizeDp: Float, color: Color) {
        out += linkedMapOf(
          "kind" to "text",
          "text" to text,
          "x" to round3(mapX(FluidMenuRowSidePadding.value * d) / d),
          "y" to round3(mapY(top + baselineDp * d) / d),
          "sizeDp" to round3(sizeDp * scale),
          "color" to glassColor(color),
        )
      }
      if (sub == null) {
        text(e.action.label, BaselineDp, 17f, tone)
      } else {
        text(e.action.label, SubtitleLabelBaselineDp, 17f, tone)
        text(sub, SubtitleBaselineDp, 15f, tone.copy(alpha = tone.alpha * 0.6f))
      }
      // I segni a destra, dal bordo: il gallone o la spunta nella colonna di destra, l'icona prima di loro.
      val marks = ArrayList<Pair<String, Color>>()
      when {
        e.kind == FluidMenuCellKind.Header -> marks += ChevronOpen to tone.copy(alpha = tone.alpha * 0.6f)
        e.opensSubmenu -> marks += Chevron to tone.copy(alpha = tone.alpha * 0.6f)
        e.action.selected -> marks += Check to tone
      }
      Icons[e.action.label]?.let { marks += it to tone }
      var right = box.width - FluidMenuRowSidePadding.value * d
      for ((icon, color) in marks) {
        val glyph = FluidMenuRowGlyph.value * d
        out += linkedMapOf(
          "kind" to "icon",
          "icon" to icon,
          "x" to round3(mapX(right - glyph / 2f) / d),
          "y" to round3(mapY(top + h / 2f) / d),
          "sizeDp" to round3(FluidMenuRowGlyph.value * scale),
          "color" to glassColor(color),
        )
        right -= glyph + FluidMenuRowGap.value * d
      }
    }
    return out
  }

  /** Il colore di una voce, come `FluidGlassMenuRow` (l'intestazione e' sempre l'inchiostro pieno). */
  private fun tone(e: FluidMenuEntry, palette: TodayPalette): Color {
    val header = e.kind == FluidMenuCellKind.Header
    val base = if (e.action.destructive && !header) palette.error else palette.onSurface
    return if (e.action.enabled || header) base else base.copy(alpha = base.alpha * 0.38f)
  }

  private fun head(from: Rect, to: Rect, opening: Boolean): Map<String, Any?> {
    val d = Density
    val midX = round3((from.center.x / d) - CanvasOriginDp.x)
    return linkedMapOf(
      "_" to "F5A.3: il menu di un'azione di barra, gia' aperto, che diventa il sottomenu di «Ordina» " +
        (if (opening) "(apre)" else "(torna)") + " dentro lo stesso vetro. Celle, pannelli e curve dall'engine, " +
        "orologio e posto del testo ricostruiti (F5a3MenuStructureTraces.kt). Nessuna clip di Apple mostra un " +
        "sottomenu che si apre: il confronto e' con le regole misurate (la molla del menu, il cambio di contenuto R7).",
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to "#EAEAEA"),
      "page" to listOf(
        linkedMapOf(
          "kind" to "background",
          "x" to 0,
          "y" to 0,
          "w" to CanvasWidthDp,
          "h" to CanvasHeightDp,
          "background" to linkedMapOf(
            "kind" to "synthetic",
            "scaleDp" to 46,
            "detail" to 0.5,
            "palette" to listOf("#E6E6E6", "#5B4BA8", "#E9E9E9", "#3F7A45", "#D2D2D2", "#C9B9A0"),
          ),
        ),
      ),
      "events" to listOf(linkedMapOf("t" to 0, "kind" to "mark", "label" to if (opening) "tocco su «Ordina»" else "tocco sull'intestazione")),
      "probes" to listOf(
        linkedMapOf(
          "name" to "bordo-basso",
          "kind" to "edge",
          "line" to "v:$midX:0:$CanvasHeightDp",
          "which" to "last",
          "percentOf" to round3(to.height / d),
          "label" to "il bordo basso del vetro, dai pixel; in % dell'altezza del pannello d'arrivo",
        ),
        linkedMapOf(
          "name" to "alfa-voci",
          "kind" to "value",
          "path" to "menu.content[voci].alpha",
          "label" to "l'opacita' del livello che arriva (lo strato voci); quello che se ne va e' lo strato uscenti, sotto",
        ),
      ),
    )
  }

  /** Le voci di `OggiMenuApertura` con una sezione, nell'ordine che il menu dichiarato con [order] mostra verso l'alto. */
  fun orderedScene(name: String, reversed: Boolean): MenuScene {
    val base = OggiMenuApertura.scene(density = Density)
    val actions = listOf(
      FluidContextAction("Recents") {},
      FluidContextAction("Orders") {},
      FluidContextAction("Wishlist") {},
      FluidContextAction("Tracking", startsSection = true) {},
    )
    val icons = mapOf(
      "Recents" to "fluid:history|circle",
      "Orders" to "fluid:person|circle",
      "Wishlist" to "fluid:favorite|circle",
      "Tracking" to "fluid:archive|circle",
    )
    val entries = fluidMenuEntries(actions, FluidMenuElementSize.Large, reversed)
    return MenuScene(
      name = name,
      fps = base.fps,
      density = base.density,
      hostDp = base.hostDp,
      anchorDp = base.anchorDp,
      canvasOriginDp = base.canvasOriginDp,
      canvasWidthDp = base.canvasWidthDp,
      canvasHeightDp = base.canvasHeightDp,
      longPressMs = base.longPressMs,
      preambleMs = base.preambleMs,
      items = entries.map { MenuItemSpec(it.action.label, icons.getValue(it.action.label), startsSection = it.startsSection) },
      faceIcon = base.faceIcon,
      faceIconSizeDp = base.faceIconSizeDp,
      labelDpPerChar = base.labelDpPerChar,
      maxMs = base.maxMs,
      tailFrames = base.tailFrames,
      head = base.head,
      still = base.still,
    )
  }

  private const val BaselineDp = 30f
  private const val SubtitleLabelBaselineDp = 27f
  private const val SubtitleBaselineDp = 48f
}
