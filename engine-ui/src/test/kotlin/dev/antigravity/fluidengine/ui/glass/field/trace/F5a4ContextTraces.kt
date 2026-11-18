package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextScrimLevel
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchorGap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuDropSeedDiameter
import dev.antigravity.fluidengine.ui.fluid.FluidMenuDropSeedOverlap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuEdgeMargin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationEdgeMin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationReferenceHeight
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLayerFadeInMillis
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelPadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelWidth
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowGlyph
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRowSidePadding
import dev.antigravity.fluidengine.ui.fluid.FluidMenuSectionGap
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidPopoverRadius
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScrimLook
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.fluidContextAnchorScale
import dev.antigravity.fluidengine.ui.fluid.fluidContextLiftTarget
import dev.antigravity.fluidengine.ui.fluid.fluidDropInflation
import dev.antigravity.fluidengine.ui.fluid.fluidLiftContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidLiftProgress
import dev.antigravity.fluidengine.ui.fluid.fluidLiftStartFraction
import dev.antigravity.fluidengine.ui.fluid.fluidMenuContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuDropPresence
import dev.antigravity.fluidengine.ui.fluid.fluidMenuDropSeed
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLiquidContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPanelLayout
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPanelWidth
import dev.antigravity.fluidengine.ui.fluid.fluidMenuRowHeight
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluid.solveFluidMenuPanel
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLargeVeil
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLiquidRecipes
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.physicsFormShortPx
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidRecipe
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRecipeShortPx
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Le tracce del menu contestuale di F5A.4 (`GLASS_TRACE_DIR`, fase `f5a-4`): una riga di una lista tenuta premuta, il
 * click lungo, la pagina che va dietro lo scrim, la riga che si solleva, il menu che nasce da una goccia appesa alla
 * riga; poi, a menu fermo, il congedo e il ritorno.
 *
 * **Dall'engine**, chiamate come le chiama il codice: la scala della riga sotto il dito (`fluidContextAnchorScale` con la
 * molla `FluidMotion.DropPress` del bagliore), dove si posa la card (`fluidContextLiftTarget`), da dove parte e come la
 * segue l'immagine (`fluidLiftStartFraction`, `fluidLiftProgress`, `fluidLiftContentMap`), le misure e il posto del
 * pannello (`fluidMenuPanelLayout`, `fluidMenuPanelWidth`, `solveFluidMenuPanel` accanto alla card), la goccia
 * (`fluidMenuDropSeed`, `fluidMenuDropPresence`), il viaggio (`FluidMenuJourney.prepare`/`install`), le finestre del
 * contenuto (`fluidMenuContentAlpha`, `fluidMenuLiquidContentMap`, `travelClipIn`), lo scrim (`FluidScrimLook`), il
 * materiale (`physicsLiquidRecipe`, `PhysicsLargeVeil`).
 *
 * **Ricostruito.** L'orologio (le molle in forma chiusa, `TodaySpring`; la goccia con `TodayLiquidDriver`); la pagina
 * (una lista di quattro righe su un fondo raggruppato, una striscia di foto sopra: si vede cosa fa la sfocatura); il
 * testo della riga (titolo 16 sp, sottotitolo 14 sp, a occhio sulla tipografia di `ListItem`); lo scrim come uno shader
 * di servizio (`velo-scrim.agsl`: la pagina sfocata per il nero del livello, posata con la sua dissolvenza, che e' cio'
 * che fa il vetro di prima per un velo piatto).
 *
 * **Fuori dalla traccia.** La luce del dito sulla riga (il bagliore di oggi non e' rendibile, TRACE.md), la banda grigia
 * fra le sezioni del pannello, la goccia che segue il dito sulle voci.
 */

/** Una riga della lista della scena: titolo e sottotitolo. */
private class ContextRow(val title: String, val subtitle: String)

/** La traccia e i fatti che il test controlla. */
internal class ContextBuilt(
  val opens: GlassTrace,
  val closes: GlassTrace,
  /** La riga tenuta premuta, la card a riposo, il pannello: pixel dello schermo. */
  val row: Rect,
  val lifted: Rect,
  val panel: Rect,
  /** La sagoma della card al primo fotogramma dopo il click lungo (pixel dello schermo). */
  val cardAtLongPress: Rect,
  /** La sagoma della card all'ultimo fotogramma del ritorno. */
  val cardAtEnd: Rect,
  /** La scala della riga sotto il dito al click lungo. */
  val anchorScaleAtLongPress: Float,
)

internal object F5a4ContextTraces {
  const val Phase = "f5a-4"

  private const val D = 3f
  private const val Fps = 60

  /** Il telefono di Apple delle scene dei menu (OggiMenuApertura): 411 x 891 dp. */
  private val HostDp = Size(411f, 891f)

  /** La tela: la parte dello schermo con la lista e il menu. */
  private val CanvasOriginDp = Offset(0f, 260f)
  private const val CanvasWidthDp = 411f
  private const val CanvasHeightDp = 520f

  /** La striscia di foto in cima alla tela e il gruppo della lista, in dp dello schermo. */
  private const val PhotoTopDp = 276f
  private const val PhotoHeightDp = 120f
  private const val GroupLeftDp = 16f
  private const val GroupRightDp = 395f
  private const val GroupTopDp = 412f
  private const val RowHeightDp = 64f

  private val Rows = listOf(
    ContextRow("Nota della riunione", "Tieni premuto, trascina, rilascia"),
    ContextRow("Lista della spesa", "Rilascia fermo, poi tocca"),
    ContextRow("Idee per il viaggio", "Tre giorni a Lisbona"),
    ContextRow("Bollette", "Scadono il 30"),
  )

  /** La riga tenuta premuta. */
  private const val Pressed = 1

  /** Dal contatto al click lungo: il `longPressTimeoutMillis` di serie di Android. */
  private const val LongPressMs = 400.0

  /** Il preambolo mostrato: la riga che si gonfia sotto il dito. */
  private const val PreambleMs = 200.0

  /** Il congedo, a menu fermo: un tocco sullo scrim. */
  private const val DismissAtMs = 900.0

  /** Le voci di `demoActions` della galleria: quattro, l'ultima apre una sezione. */
  private val Items = listOf(
    MenuItemSpec("Condividi", "fluid:share|share"),
    MenuItemSpec("Aggiungi ai preferiti", "fluid:star.fill|circle"),
    MenuItemSpec("Modifica (spenta)", "fluid:edit|circle", enabled = false),
    MenuItemSpec("Elimina", "fluid:delete|close", destructive = true, startsSection = true),
  )

  /** Dalla cima della riga del menu alla linea di base del testo (17 sp su una riga da 48 dp), come `OggiMenuApertura`. */
  private const val MenuBaselineDp = 30f

  private fun suffix(level: FluidContextScrimLevel, dark: Boolean) =
    "-" + level.name.lowercase() + if (dark) "-scuro" else ""

  fun openName(level: FluidContextScrimLevel, dark: Boolean) = "contestuale-apre" + suffix(level, dark)

  fun closeName(level: FluidContextScrimLevel, dark: Boolean) = "contestuale-chiude" + suffix(level, dark)

  private fun rowRectDp(i: Int) = Rect(GroupLeftDp, GroupTopDp + i * RowHeightDp, GroupRightDp, GroupTopDp + (i + 1) * RowHeightDp)

  private fun Rect.px() = Rect(left * D, top * D, right * D, bottom * D)

  fun build(level: FluidContextScrimLevel, dark: Boolean): ContextBuilt {
    val look: FluidScrimLook = level.look
    val palette = TodayPalette(dark)
    val host = Size(HostDp.width * D, HostDp.height * D)
    val origin = Offset(CanvasOriginDp.x * D, CanvasOriginDp.y * D)
    val row = rowRectDp(Pressed).px()

    // Le misure come le costruisce `rememberFluidMenuPanelMetrics`, con la riga di `bodyLarge` (17/23 sp).
    val metrics = FluidMenuPanelMetrics(
      widthPx = FluidMenuPanelWidth.value * D,
      rowHeightPx = fluidMenuRowHeight(23.dp).value * D,
      paddingPx = FluidMenuPanelPadding.value * D,
      sectionGapPx = FluidMenuSectionGap.value * D,
      radiusPx = FluidRadius.Group.value * D,
      gapPx = FluidMenuAnchorGap.value * D,
      marginPx = FluidMenuEdgeMargin.value * D,
      edgeMinPx = FluidMenuInflationEdgeMin.value * D,
      refHeightPx = FluidMenuInflationReferenceHeight.value * D,
    )
    val layout = fluidMenuPanelLayout(Items.map { it.startsSection }, metrics)
    val width = fluidMenuPanelWidth(
      metrics = metrics,
      labelWidthsPx = Items.map { it.label.length * 8.5f * D },
      hasIcon = true,
      hasCheck = false,
      density = D,
      hostWidthPx = host.width,
    )
    // Come `FluidMenuLayer` (F5A.4): la card si posa sollevata e il pannello le sta accanto.
    val lifted = fluidContextLiftTarget(row, null, host, metrics.marginPx)
    val panel = solveFluidMenuPanel(
      anchor = lifted,
      host = host,
      width = width,
      height = layout.height,
      gapPx = metrics.gapPx,
      marginPx = metrics.marginPx,
      placement = FluidMenuPlacement.BesideAnchor,
    )
    val panelBox = Rect(panel.left.roundToInt().toFloat(), panel.top.roundToInt().toFloat(), panel.right, panel.bottom)

    // Il dito: giu' a -LongPressMs, fermo al centro della riga; la luce del dito gonfia la riga con la molla del bagliore.
    val touchX = row.center.x
    val press = TodaySpring(FluidMotion.DropPress, 0f, 1f)
    fun anchorScaleAt(tMs: Double): Float =
      if (tMs < -LongPressMs) 1f else fluidContextAnchorScale(press.value(((tMs + LongPressMs) * 1_000_000.0).toLong()))
    val s0 = anchorScaleAt(0.0)

    // La card: da dove la riga e' arrivata sotto il dito, alla riga sollevata, con la molla del menu.
    val cardPhysics = physics()
    val radius = FluidPopoverRadius.value * D
    var cardStart = fluidLiftStartFraction(row, lifted, s0)
    var p = 0f
    cardPhysics.driveExternally(
      from = FluidForm.Slab(row, FluidCornerRadii.all(radius.coerceAtMost(row.minDimension / 2f))),
      to = FluidForm.Slab(lifted, FluidCornerRadii.all(radius.coerceAtMost(lifted.minDimension / 2f))),
      progress = { fluidLiftProgress(p.coerceAtLeast(0f), cardStart) },
      overshootInflationX = FluidMotion.LiftedSwellX,
      overshootInflationY = FluidMotion.LiftedSwellY,
    )

    // Il menu: dalla goccia appesa alla card, sotto il dito.
    val menuPhysics = physics()
    val journey = FluidMenuJourney()
    journey.prepare(
      anchor = null,
      hostOrigin = Offset.Zero,
      panel = panel,
      radius = metrics.radiusPx,
      inflation = fluidDropInflation(panel.width, host.width, metrics.edgeMinPx),
      seed = fluidMenuDropSeed(
        row = lifted,
        panel = panel,
        touchX = touchX,
        diameterPx = FluidMenuDropSeedDiameter.value * D,
        overlapPx = FluidMenuDropSeedOverlap.value * D,
      ),
      freshStart = true,
    )
    journey.install(menuPhysics)

    val cardDriver = TodayLiquidDriver(cardPhysics, D)
    val menuDriver = TodayLiquidDriver(menuPhysics, D)
    val panelOptics = GlassDefaults.panelOptics().sanitized()
    val compactOptics = GlassDefaults.panelOptics(compact = true).sanitized()
    val cardDress = ContextDress(GlassRole.Modal, PhysicsMaterialSource(panelOptics, panelOptics), D, dark)
    val menuDress = ContextDress(GlassRole.Modal, PhysicsMaterialSource(compactOptics, compactOptics), D, dark)
    // La pellicola dei pannelli (`panelTint()`, neutra) e quella della finestra del menu, che ci compone sotto il nero
    // dello scrim del contestuale perche' campiona la pagina cruda (`FluidMenuLayer`, `windowTint`).
    val panelTint = GlassTint(overlay = palette.modalOverlay, fallback = Color.Transparent, hairline = Color.Transparent, mode = GlassTintMode.Neutral)
    val windowTint = panelTint.copy(overlay = panelTint.overlay.compositeOver(look.color(dark)))

    val openSpring = TodaySpring(FluidMotion.dropOpen(), 0f, 1f)
    val fadeIn = TodaySpring(FluidMotion.fadeIn(FluidMenuLayerFadeInMillis), 0f, 1f)
    val dismissNanos = (DismissAtMs * 1_000_000.0).toLong()
    var closeSpring: TodaySpring? = null
    var fadeOut: TodaySpring? = null

    val opens = ArrayList<GlassTraceFrame>()
    val closes = ArrayList<GlassTraceFrame>()
    var cardAtLongPress: Rect? = null
    var cardAtEnd = row

    // Il preambolo: la pagina con la riga che si gonfia sotto il dito, niente host.
    val preambleFrames = ceil(PreambleMs * Fps / 1000.0 - 1e-9).toInt()
    for (k in -preambleFrames until 0) {
      val t = glassTraceTimeMs(k, Fps)
      opens += GlassTraceFrame(t, listOf(pressedRow(palette, anchorScaleAt(t))))
    }

    val dismissFrame = (DismissAtMs * Fps / 1000.0).roundToInt()
    var k = 0
    var tail = 0
    while (k < dismissFrame + 120) {
      val nanos = glassTraceTimeNanos(k, Fps)
      val tMs = glassTraceTimeMs(k, Fps)
      val closing = nanos >= dismissNanos
      if (closing && closeSpring == null) {
        // Il congedo: la molla del ritorno da dove il menu e' fermo, la dissolvenza d'uscita; a dito alzato la partenza
        // della card torna viva (scala 1, frazione zero).
        closeSpring = TodaySpring(FluidMotion.dropClose(), p, 0f)
        fadeOut = TodaySpring(FluidMotion.fadeOut(FluidMotion.MenuFadeOutMillis), 1f, 0f)
        cardStart = fluidLiftStartFraction(row, lifted, 1f)
      }
      val since = nanos - dismissNanos
      p = if (closing) closeSpring!!.value(since) else openSpring.value(nanos)
      val fade = if (closing) fadeOut!!.value(since) else fadeIn.value(nanos)
      runBlocking { journey.progress.snapTo(p) }
      cardDriver.frame(nanos)
      menuDriver.frame(nanos)
      val cardPlan = cardPhysics.ensurePlan()
      val menuPlan = menuPhysics.ensurePlan()
      val cardBounds = cardPhysics.contentBoundsNow()
      if (k == 0) cardAtLongPress = cardBounds
      cardAtEnd = cardBounds

      val elements = ArrayList<Map<String, Any?>>()
      if (fade > 0.004f) elements += scrim(look, dark, fade)
      if (fade > 0.004f) {
        elements += cardDress.element("card", cardPhysics, cardPlan, origin, CanvasWidthDp, CanvasHeightDp, rowContent(palette, cardBounds, origin), panelTint, fade, tMs)
      } else {
        // Il materiale e' spento: resta l'immagine della riga, mai sfumata, sulla sagoma.
        elements += textOnly("card", rowContent(palette, cardBounds, origin))
      }
      val windowAmount = fade * fluidMenuDropPresence(p)
      if (windowAmount > 0.004f) {
        elements += menuDress.element(
          "menu", menuPhysics, menuPlan, origin, CanvasWidthDp, CanvasHeightDp,
          menuContent(palette, layout, panel, panelBox, menuPhysics, p, closing, origin),
          windowTint, windowAmount, tMs,
        )
      }
      val frame = GlassTraceFrame(tMs, elements)
      if (!closing) opens += frame
      // Il ritorno: da qualche fotogramma prima del congedo, con t = 0 al congedo.
      if (nanos >= dismissNanos - 3 * 1_000_000_000L / Fps) {
        closes += GlassTraceFrame(glassTraceTimeMs(k - dismissFrame, Fps), elements)
      }
      if (closing) {
        val settled = closeSpring!!.finished(since) && fadeOut!!.finished(since) && menuPhysics.liquid.quiet && cardPhysics.liquid.quiet
        if (settled) tail++ else tail = 0
        if (tail > 3) break
      }
      k++
    }
    // A uscita finita la riga torna nella pagina (`entry.lifted = false`): l'ultimo fotogramma del ritorno.
    val endT = glassTraceTimeMs(k + 1 - dismissFrame, Fps)
    closes += GlassTraceFrame(endT, listOf(pressedRow(palette, 1f)))

    val head = head(palette, dark, panel, lifted, level)
    val theme = if (dark) "dark" else "light"
    return ContextBuilt(
      opens = GlassTrace(openName(level, dark), Phase, D, theme, CanvasWidthDp, CanvasHeightDp, opens, Fps, head + ("apple" to appleOpen())),
      closes = GlassTrace(closeName(level, dark), Phase, D, theme, CanvasWidthDp, CanvasHeightDp, closes, Fps, head + ("apple" to appleClose())),
      row = row,
      lifted = lifted,
      panel = panel,
      cardAtLongPress = cardAtLongPress!!,
      cardAtEnd = cardAtEnd,
      anchorScaleAtLongPress = s0,
    )
  }

  private fun physics() = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
    density = D
    liquidAllowed = true
    maskInShader = true
    liquidBody = true
  }

  // --- La pagina --------------------------------------------------------------------------------

  private fun canvasDp(x: Float, y: Float) = Offset(x - CanvasOriginDp.x, y - CanvasOriginDp.y)

  /**
   * La pagina, cioe' cio' che lo scrim e il vetro campionano: la striscia di foto, il gruppo della lista e le sue righe,
   * **tranne il testo della riga premuta**. Sollevata, quella riga non si disegna nella pagina (lo spazio resta, il
   * contenuto lo disegna l'host); prima del click lungo la disegna [pressedRow], un elemento sopra la pagina che
   * nessun vetro campiona ancora.
   */
  private fun page(palette: TodayPalette, dark: Boolean): List<Map<String, Any?>> {
    val out = ArrayList<Map<String, Any?>>()
    val photo = canvasDp(GroupLeftDp, PhotoTopDp)
    out += linkedMapOf(
      "kind" to "background",
      "x" to round3(photo.x), "y" to round3(photo.y),
      "w" to round3(GroupRightDp - GroupLeftDp), "h" to PhotoHeightDp,
      "radius" to 12,
      "background" to linkedMapOf(
        "kind" to "synthetic", "scaleDp" to 46, "detail" to 0.5,
        "palette" to listOf("#E9A23B", "#3A7BD5", "#2F5D34", "#F2994A", "#8E54E9", "#D2D2D2"),
      ),
    )
    val group = canvasDp(GroupLeftDp, GroupTopDp)
    out += linkedMapOf(
      "kind" to "rect",
      "x" to round3(group.x), "y" to round3(group.y),
      "w" to round3(GroupRightDp - GroupLeftDp), "h" to round3(RowHeightDp * Rows.size),
      "color" to if (dark) "#1C1C1E" else "#FFFFFF",
      "radius" to 12,
    )
    Rows.forEachIndexed { i, r ->
      val rect = rowRectDp(i)
      if (i > 0) {
        val line = canvasDp(GroupLeftDp + 16f, rect.top)
        out += linkedMapOf("kind" to "rect", "x" to round3(line.x), "y" to round3(line.y), "w" to round3(GroupRightDp - GroupLeftDp - 16f), "h" to 0.34, "color" to if (dark) "#38383A" else "#E0E0E3")
      }
      if (i == Pressed) return@forEachIndexed
      for (item in rowTexts(palette, r, rect, 1f)) {
        val at = canvasDp((item["x"] as Double).toFloat(), (item["y"] as Double).toFloat())
        out += LinkedHashMap(item).apply {
          this["x"] = round3(at.x)
          this["y"] = round3(at.y)
        }
      }
    }
    return out
  }

  /** Il titolo e il sottotitolo di una riga in dp dello schermo, scalati di [scale] attorno al centro della riga. */
  private fun rowTexts(palette: TodayPalette, r: ContextRow, rectDp: Rect, scale: Float): List<Map<String, Any?>> {
    val c = rectDp.center
    fun sx(x: Float) = c.x + (x - c.x) * scale
    fun sy(y: Float) = c.y + (y - c.y) * scale
    return listOf(
      linkedMapOf("kind" to "text", "text" to r.title, "x" to round3(sx(rectDp.left + 16f)), "y" to round3(sy(rectDp.top + 28f)), "sizeDp" to round3(16f * scale), "color" to glassColor(palette.onSurface)),
      linkedMapOf("kind" to "text", "text" to r.subtitle, "x" to round3(sx(rectDp.left + 16f)), "y" to round3(sy(rectDp.top + 49f)), "sizeDp" to round3(14f * scale), "color" to glassColor(palette.onSurface.copy(alpha = 0.62f))),
    )
  }

  /** L'immagine della riga sulla card: gli stessi testi, portati sulla sagoma di adesso da `fluidLiftContentMap`. */
  private fun rowContent(palette: TodayPalette, cardBounds: Rect, origin: Offset): List<Map<String, Any?>> {
    val rowPx = rowRectDp(Pressed).px()
    val map = fluidLiftContentMap(target = rowPx, current = cardBounds)
    val cDp = Offset(rowPx.center.x / D + map.translation.x / D, rowPx.center.y / D + map.translation.y / D)
    val rowDp = rowRectDp(Pressed)
    val items = rowTexts(palette, Rows[Pressed], rowDp.translate(cDp.x - rowDp.center.x, cDp.y - rowDp.center.y), map.scale).map { item ->
      LinkedHashMap(item).apply {
        this["x"] = round3((item["x"] as Double).toFloat() - origin.x / D)
        this["y"] = round3((item["y"] as Double).toFloat() - origin.y / D)
      }
    }
    return listOf(
      linkedMapOf("name" to "riga", "x" to 0, "y" to 0, "w" to CanvasWidthDp, "h" to CanvasHeightDp, "items" to items),
    )
  }

  /** La riga premuta nella pagina, scalata di [swell] sotto il dito attorno al centro (il `graphicsLayer` dell'ancora). */
  private fun pressedRow(palette: TodayPalette, swell: Float): Map<String, Any?> {
    val rowDp = rowRectDp(Pressed)
    val items = rowTexts(palette, Rows[Pressed], rowDp, swell).map { item ->
      LinkedHashMap(item).apply {
        val at = canvasDp((item["x"] as Double).toFloat(), (item["y"] as Double).toFloat())
        this["x"] = round3(at.x)
        this["y"] = round3(at.y)
      }
    }
    return textOnly("riga", listOf(linkedMapOf("name" to "riga", "x" to 0, "y" to 0, "w" to CanvasWidthDp, "h" to CanvasHeightDp, "items" to items)))
  }

  /** La riga senza vetro (a materiale spento): un elemento `shape` trasparente che porta solo il contenuto. */
  private fun textOnly(name: String, content: List<Map<String, Any?>>): Map<String, Any?> = linkedMapOf(
    "kind" to "shape",
    "name" to name,
    "x" to 0, "y" to 0, "w" to CanvasWidthDp, "h" to CanvasHeightDp,
    "radius" to 0,
    "color" to "#00000000",
    "content" to content,
    "contentClip" to "none",
  )

  // --- Lo scrim -------------------------------------------------------------------------------------

  private fun scrim(look: FluidScrimLook, dark: Boolean, fade: Float): Map<String, Any?> = linkedMapOf(
    "kind" to "shader",
    "name" to "scrim",
    "source" to "file:tools/glassbench/traces/f5a-4/velo-scrim.agsl",
    "x" to 0, "y" to 0, "w" to CanvasWidthDp, "h" to CanvasHeightDp,
    "radius" to 0,
    "inputs" to linkedMapOf("content" to linkedMapOf("saturation" to 1, "blurDp" to round3(look.blurDp), "edge" to "clamp")),
    "uniforms" to linkedMapOf("dim" to look.alpha(dark)),
    "alpha" to round3(fade.coerceIn(0f, 1f)),
  )

  // --- Il menu --------------------------------------------------------------------------------------

  private fun menuContent(
    palette: TodayPalette,
    layout: dev.antigravity.fluidengine.ui.fluid.FluidMenuPanelLayout,
    panel: Rect,
    panelBox: Rect,
    physics: FluidPhysicsState,
    p: Float,
    closing: Boolean,
    origin: Offset,
  ): List<Map<String, Any?>> {
    val alpha = fluidMenuContentAlpha(p, closing = closing)
    if (alpha <= 0f) return emptyList()
    val map = fluidMenuLiquidContentMap(target = panel, current = physics.contentBoundsNow(), alpha = alpha)
    val clip = physics.travelClipIn(panelBox, p)
    val cx = panelBox.width / 2f
    val cy = panelBox.height / 2f
    fun mapX(x: Float) = panelBox.left + cx + (x - cx) * map.scale + map.translation.x - origin.x
    fun mapY(y: Float) = panelBox.top + cy + (y - cy) * map.scale + map.translation.y - origin.y
    val items = ArrayList<Map<String, Any?>>()
    Items.forEachIndexed { i, item ->
      val top = layout.tops[i]
      val base = if (item.destructive) palette.error else palette.onSurface
      val tone = if (item.enabled) base else base.copy(alpha = base.alpha * 0.38f)
      items += linkedMapOf(
        "kind" to "text", "text" to item.label,
        "x" to round3(mapX(FluidMenuRowSidePadding.value * D) / D),
        "y" to round3(mapY(top + MenuBaselineDp * D) / D),
        "sizeDp" to round3(17f * map.scale),
        "color" to glassColor(tone),
      )
      items += linkedMapOf(
        "kind" to "icon", "icon" to item.icon,
        "x" to round3(mapX(panelBox.width - (FluidMenuRowSidePadding.value + FluidMenuRowGlyph.value / 2f) * D) / D),
        "y" to round3(mapY(top + layout.rowHeightPx / 2f) / D),
        "sizeDp" to round3(FluidMenuRowGlyph.value * map.scale),
        "color" to glassColor(tone),
      )
    }
    val layer = linkedMapOf<String, Any?>(
      "name" to "voci", "x" to 0, "y" to 0, "w" to CanvasWidthDp, "h" to CanvasHeightDp,
      "items" to items, "alpha" to alpha,
    )
    if (clip != null) {
      val onCanvas = clip.translate(Offset(panelBox.left - origin.x, panelBox.top - origin.y))
      layer["clip"] = linkedMapOf(
        "x" to round3(onCanvas.left / D), "y" to round3(onCanvas.top / D),
        "w" to round3(onCanvas.width / D), "h" to round3(onCanvas.height / D),
        "radius" to listOf(
          onCanvas.topLeftCornerRadius.x, onCanvas.topRightCornerRadius.x,
          onCanvas.bottomRightCornerRadius.x, onCanvas.bottomLeftCornerRadius.x,
        ).map { round3(it / D) },
      )
    }
    return listOf(layer)
  }

  // --- La testa ---------------------------------------------------------------------------------

  private fun head(palette: TodayPalette, dark: Boolean, panel: Rect, lifted: Rect, level: FluidContextScrimLevel): Map<String, Any?> {
    val liftedDp = lifted.translate(-CanvasOriginDp.x * D, -CanvasOriginDp.y * D)
    val midX = round3(liftedDp.center.x / D)
    return linkedMapOf(
      "_" to "F5A.4: il menu contestuale di una riga, livello di sfondo ${level.name} " +
        "(nero ${level.look.lightAlpha}/${level.look.darkAlpha}, sfocatura ${level.look.blurDp} dp). Numeri dalle funzioni " +
        "dell'engine, orologio e pagina ricostruiti (F5a4ContextTraces.kt). Senza luce del dito ne' banda fra le sezioni.",
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to if (dark) "#000000" else "#F2F2F7"),
      "page" to page(palette, dark),
      "events" to listOf(linkedMapOf("t" to 0, "kind" to "mark", "label" to "click lungo")),
      "probes" to listOf(
        linkedMapOf(
          "name" to "larghezza-card",
          "kind" to "width",
          "line" to "h:${round3(liftedDp.center.y / D)}:0:$CanvasWidthDp",
          "label" to "la larghezza della card sollevata, dai pixel",
        ),
        linkedMapOf(
          "name" to "luce-pagina",
          "kind" to "luma",
          "rect" to listOf(40, round3((PhotoTopDp - CanvasOriginDp.y) + 20f), 120, 60),
          "label" to "la foto in cima alla pagina: quanto scurisce lo sfondo",
        ),
        linkedMapOf(
          "name" to "bordo-alto-menu",
          "kind" to "edge",
          "line" to "v:$midX:${round3((panel.top / D) - CanvasOriginDp.y - 30f)}:$CanvasHeightDp",
          "which" to "first",
          "label" to "il bordo alto del vetro del menu sotto la card",
        ),
      ),
    )
  }

  /**
   * Apple: nel materiale non c'e' un menu contestuale da pressione lunga (R1 X1: tempi e scala non documentati; R4 non
   * li misura). Il confronto e' col menu che nasce da un tasto del telefono, R4 B1 (a), con l'orologio corretto: `t = 0`
   * e' l'ultimo fotogramma fermo prima del cambio (f9147 all'apertura, f9186 alla chiusura, dalle differenze fra
   * fotogrammi sul ritaglio). Vale per le fasi del menu (nascita, superamento, contenuto), non per il sollevamento ne'
   * per lo sfondo. Il ritaglio ha le proporzioni della tela, a ~1,9 px del video per punto.
   */
  private fun appleOpen(): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to "telefono",
      "video" to "wwdc2025-219_hd",
      "t0Frame" to 9147,
      "crop" to listOf(590, 0, 600, 760),
      "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
      "note" to "R4 B1 (a), classe A: il menu dal tasto in basso a sinistra. Solo per il menu: il contestuale di Apple non c'e'.",
    ),
  )

  private fun appleClose(): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to "telefono",
      "video" to "wwdc2025-219_hd",
      "t0Frame" to 9186,
      "crop" to listOf(590, 0, 600, 760),
      "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_chiusura_f9185-9200.jpg",
      "note" to "R4 B1 (a): la chiusura del menu dal tasto (~200 ms di sagoma). L'engine chiude in ~0,5 s per scelta di Alessio.",
    ),
  )
}

/**
 * Il vestito `Liquid` di una superficie fisica del contestuale: come [LiquidPhysicsDress], con in piu' l'intensita' (il
 * materiale che entra ed esce con lo scrim) e la tinta della superficie (la finestra del menu ha la quota di scrim
 * nella pellicola). L'ombra in corpo dell'engine, il velo dei grandi nel tempo (F1B.2).
 */
private class ContextDress(
  private val role: GlassRole,
  private val material: PhysicsMaterialSource,
  private val density: Float,
  private val darkSide: Boolean,
) {
  private val profile = GlassLiquidProfile.Default
  private val paddingDp: Float = glassTravelPaddingDp(role, material.target, material.origin, profile)
  private val recipes = PhysicsLiquidRecipes()
  private val follower = PhysicsLargeVeil()

  private fun largeVeil(state: FluidPhysicsState, plan: PhysicsRenderPlan, timeMs: Double): Float {
    val now = glassLiquidLargeVeilGain(physicsRecipeShortPx(plan) / density)
    val origin = state.journeyOriginNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it) / density) }
    val target = state.journeyTargetNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it) / density) }
    return follower.next(origin ?: now, target ?: now, (timeMs * 1_000_000.0).toLong())
  }

  fun element(
    name: String,
    state: FluidPhysicsState,
    plan: PhysicsRenderPlan,
    origin: Offset,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    content: List<Map<String, Any?>>,
    tint: GlassTint,
    amount: Float,
    timeMs: Double,
  ): Map<String, Any?> {
    val recipe = physicsLiquidRecipe(
      role = role,
      material = material.at(1f),
      shortDp = physicsRecipeShortPx(plan) / density,
      darkSide = darkSide,
      amount = amount.coerceIn(0f, 1f),
      quality = 1f,
      press = 0f,
      tint = tint,
      tintFrom = null,
      tintBlend = 1f,
      nested = false,
      motion = state.liquid.lens,
      profile = profile,
      scratch = recipes,
      largeVeil = largeVeil(state, plan, timeMs),
    )
    val rest = state.restFormNow() != null
    val inputs = liquidPhysicsInputs(plan, recipe, density, paddingDp, rest, shadow = true)
    return liquidPhysicsElement(
      name = name,
      inputs = inputs,
      origin = origin,
      density = density,
      canvasWidthDp = canvasWidthDp,
      canvasHeightDp = canvasHeightDp,
      content = content,
      note = if (rest) "fisica ferma" else "fisica in viaggio, luce in corpo",
    )
  }
}
