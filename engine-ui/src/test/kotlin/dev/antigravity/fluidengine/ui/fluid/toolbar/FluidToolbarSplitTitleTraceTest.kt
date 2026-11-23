package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPlaneDriver
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSwell
import dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidSplitButtonDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.GlassContentLook
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.GlassPieceContent
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.fluidGlassContentFlightOf
import dev.antigravity.fluidengine.ui.fluid.fluidGlassTint
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeNanos
import dev.antigravity.fluidengine.ui.glass.field.trace.F38aTraces
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.MenuBuilt
import dev.antigravity.fluidengine.ui.glass.field.trace.MenuItemSpec
import dev.antigravity.fluidengine.ui.glass.field.trace.MenuScene
import dev.antigravity.fluidengine.ui.glass.field.trace.MenuTraceVariant
import dev.antigravity.fluidengine.ui.glass.field.trace.TraceDress
import dev.antigravity.fluidengine.ui.glass.field.trace.buildMenuTrace
import java.io.File
import java.util.Locale
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.11, le tracce del banco a 120 Hz, in chiaro e in scuro. Si scrivono sempre in `build/reports/f6b-11/tracce/<nome>/`
 * e, con `GLASS_F6B11_DIR`, nella cartella delle fasi (`f6b-11/<nome>/trace.json`); si rendono con `glassmotion.py`.
 *
 * - **`tasto-cambia-un-vetro`**: la posta che entra in selezione e ne esce; l'indietro diventa «Seleziona tutto» nello
 *   stesso posto, e torna. Dalla DSL vera (`selection { }`) alla barra di produzione: `fluidToolbarModel`,
 *   `resolveFluidToolbar`, `FluidToolbarMemory` (le identità dei vetri, i contenuti che escono ed entrano), il conducente
 *   del piano (`FluidGlassPlaneDriver`), la ricetta di F38aTraces e lo scambio R7 dei contenuti (`GlassContentJourney`):
 *   lo schema dell'esportatore delle barre di F4A (`F4aToolbarExportTest.trace`), con le scritte misurate a 9 dp per
 *   carattere invece della sola «Bozza» di quelle scene. Il test vieta due vetri nel posto dell'indietro in ogni fotogramma.
 * - **`tasto-diviso-menu`**: «Invia ▾» nella barra, la freccia toccata: il menu nasce dal vetro intero del tasto (il
 *   tasto singolo della barra cede il suo vetro al menu, F5A.2: B1 a) e ci rientra; «Programma invio».
 * - **`titolo-selettore`**: «Posta in arrivo» col disco: il titolo agganciato toccato apre gli account, nati dal disco
 *   (l'ancora `Expand` sul quadrato di 44 dp attorno al disco, come in `rememberFluidTitleSelector`), e ci rientrano.
 *
 * Le due tracce del menu usano il costruttore del viaggio del menu di F5A (`buildMenuTrace`, le funzioni dell'engine):
 * apertura (t = 0 al tocco) e congedo a menu fermo (t = 0 alla richiesta), come `F6b8FilterTraces`.
 */
class FluidToolbarSplitTitleTraceTest {
  private val width = 412f
  private val height = 108f
  private val top = 32f
  private val density = 3f
  private val noop: () -> Unit = {}
  private val textWidth: (String) -> Float = { it.length * 9f + FluidToolbarDefaults.TextPadding.value * 2f }
  private val out = File("build/reports/f6b-11/tracce")
  private val phaseDir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B11_DIR"))

  private fun write(trace: GlassTrace) {
    trace.write(File(File(out, trace.name), "trace.json"))
    phaseDir?.let { trace.write(File(File(it, trace.name), "trace.json")) }
  }

  // --- tasto-cambia-un-vetro ----------------------------------------------------------------------------------------

  private fun browsing(): FluidToolbarDeclaration = buildFluidScreenToolbar(noop) {
    title(FluidToolbarTitleMode.Inline)
    selectButton(onClick = noop)
    button("cerca", FluidIcons.Search, "Cerca", onClick = noop)
  }

  private fun selecting(): FluidToolbarDeclaration = buildFluidScreenToolbar(noop) {
    title(FluidToolbarTitleMode.Inline)
    selection(FluidSelectionState<Int>().apply { select(1); select(2) }, count = null, onSelectAll = noop, onDone = noop,
      actions = FluidSelectionActions.mail(onDelete = noop, onArchive = noop, onMove = noop, onMarkRead = noop))
  }

  @Test fun `tasto-cambia-un-vetro, un vetro solo nel posto dell'indietro in ogni fotogramma`() {
    // L'entrata in selezione a 0 ms e l'uscita a 750 ms: 1,5 s a 120 Hz, ogni fotogramma nel collage.
    val inputs = listOf(Input(-50.0, "sfoglia", browsing()), Input(0.0, "seleziona", selecting()),
      Input(750.0, "sfoglia", browsing()))
    val before = resolveFluidToolbar(fluidToolbarModel(browsing(), textWidth), width, metrics = fluidToolbarMetrics())
    val slot = before.groups.single { group -> group.items.any { it.id == FluidToolbarIds.Back } }
    for (dark in listOf(false, true)) {
      val name = if (dark) "tasto-cambia-un-vetro-scuro" else "tasto-cambia-un-vetro"
      val trace = trace(name, inputs, 186, dark)
      write(trace)
      for (frame in trace.frames) {
        @Suppress("UNCHECKED_CAST")
        val groups = frame.elements.single { it["name"] == "misure" }["gruppi"] as List<Map<String, Any?>>
        val over = groups.filter {
          (it["right"] as Float) > slot.leftDp + 4f && (it["left"] as Float) < slot.leftDp + slot.widthDp - 4f
        }
        assertTrue("$name a ${frame.tMs} ms: ${over.size} vetri nel posto dell'indietro ($over)", over.size <= 1)
      }
    }
  }

  private data class Input(val at: Double, val name: String, val declaration: FluidToolbarDeclaration)
  private data class Prepared(val input: Input, val resolved: FluidToolbarResolved,
    val ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>, val plane: GlassPlaneState,
    val transfers: Map<Any, FluidToolbarTransfer>,
    val departures: List<FluidToolbarGhost>, val arrivals: Map<Any, FluidToolbarElement>)
  private data class Face(val icon: String?, val label: String?, val color: String, val size: Float) {
    fun content(): Map<String, Any?> = if (icon != null) mapOf("icon" to icon, "iconSizeDp" to size)
      else mapOf("label" to label, "labelSizeDp" to 17f, "labelWeight" to 500)
  }
  private class Slot(val group: String, val id: Any, var x: Float, var w: Float,
    var journey: GlassContentJourney, var attached: Boolean = true)

  private fun prepare(inputs: List<Input>): List<Prepared> {
    val metrics = fluidToolbarMetrics()
    val memory = FluidToolbarMemory()
    var previousAt = Double.NEGATIVE_INFINITY
    return inputs.map { input ->
      if (input.at - previousAt >= 1000.0) {
        memory.finishTransfers(memory.transfers)
        memory.ghosts.toList().forEach(memory::forget)
        memory.arrivals.values.toList().forEach(memory::finishArrival)
      }
      previousAt = input.at
      val model = fluidToolbarModel(input.declaration, textWidth,
        tintOf = { fluidToolbarTint(it, Color(0xFF007AFF), Color(0xFFFF3B30)) })
      val resolved = resolveFluidToolbar(model, width, metrics = metrics)
      memory.update(resolved, FluidToolbarMotion.Full)
      val pieces = resolved.groups.filter { it.sharedBackground }.map { group ->
        val id = memory.glassIds.getValue(group.id)
        val bounds = Rect(group.leftDp * density, top * density,
          (group.leftDp + group.widthDp) * density, (top + metrics.buttonWidthDp) * density)
        GlassPlanePiece(id.toString(), bounds, FluidCornerRadii.all(bounds.height / 2), id = id,
          transition = if (fluidToolbarGlassTransition(group) == FluidGlassTransition.MaterializeWhenAlone)
            GlassPlaneTransition.MaterializeWhenAlone else GlassPlaneTransition.MatchedGeometry,
          contentKey = fluidToolbarGlassContentKey(group))
      }
      Prepared(input, resolved, memory.glassIds.toMap(), GlassPlaneState(pieces, 16f * density,
        Rect(0f, 0f, width * density, height * density)), memory.transfers.toMap(), memory.ghosts.toList(), memory.arrivals.toMap())
    }
  }

  private fun face(item: FluidToolbarItem, dark: Boolean): Face {
    val vector = if (item.role == FluidToolbarRole.Overflow) FluidIcons.MoreHoriz else item.symbol as? ImageVector
    val icon = vector?.name?.substringAfter('.')?.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")?.lowercase(Locale.ROOT)
    return Face(icon?.let { "fluid:$it" }, item.label, if (item.isPrimary) "#FFFFFF" else if (item.selected) "#007AFF" else
      item.tintArgb?.let { String.format(Locale.ROOT, "#%06X", it and 0xFFFFFFL) } ?: if (dark) "#F2F2F7" else "#17171A",
      fluidToolbarIconSize(item.role).value)
  }

  private fun number(map: Map<String, Any?>, key: String, default: Float = 0f) = (map[key] as? Number)?.toFloat() ?: default

  /** Lo schema di `F4aToolbarExportTest.trace` (F4A.5), con le scritte generiche: vedi la testa del file. */
  private fun trace(name: String, inputs: List<Input>, last: Int, dark: Boolean): GlassTrace {
    val prepared = prepare(inputs)
    val metrics = prepared.first().resolved.metrics
    val neutral = GlassTint(Color.Transparent, Color.Transparent, Color.Transparent, GlassTintMode.Neutral)
    val tints = prepared.flatMap { state -> state.resolved.groups.map { group ->
      val primary = group.items.singleOrNull()?.takeIf { it.isPrimary }
      state.ids.getValue(group.id).toString() to if (primary == null) neutral else
        fluidGlassTint(FluidGlass.Regular.tint(Color(primary.tintArgb ?: 0xFF007AFF)), neutral)
    } }.toMap()
    val labels = prepared.flatMap { it.plane.pieces }.associate { it.key to F38aTraces.Label(icon = "plus") }
    val films = HashMap<Double, Map<String, Float?>>()
    val base = F38aTraces.build(F38aTraces.Scene(name, "F6B.11: la barra dal modello Kotlin, contenitore e contenuto veri",
      width, height, dark, prepared.first().plane, prepared.drop(1).map { F38aTraces.Step(it.input.at, it.plane) },
      labels, last, density = density, spacingDp = 16f, phase = "f6b-11",
      background = mapOf("kind" to "color", "color" to if (dark) "#24252A" else "#C4C7CE"),
      tintOf = { tints[it.key] ?: neutral })) { time, journey ->
      films[time] = tints.keys.associateWith(journey::filmAlpha)
    }
    val slots = LinkedHashMap<Pair<String, Any>, Slot>()
    var previous: Prepared? = null
    val driver = FluidGlassPlaneDriver()
    val frames = base.frames.mapIndexed { index, frame ->
      val time = frame.tMs
      val current = prepared.last { it.input.at <= time + 0.00001 }
      if (previous !== current) {
        slots.values.forEach { it.attached = false }
        val old = previous
        val reshaped = if (old == null) emptySet() else current.resolved.groups.mapNotNullTo(HashSet()) { group ->
          val glass = current.ids.getValue(group.id)
          val before = old.resolved.groups.firstOrNull { old.ids[it.id] == glass } ?: return@mapNotNullTo null
          glass.toString().takeIf {
            fluidToolbarSlotShapeKey(before, FluidToolbarKeptContent.Soft) != fluidToolbarSlotShapeKey(group, FluidToolbarKeptContent.Soft)
          }
        }
        for (group in current.resolved.groups) for (item in group.items) {
          val glass = current.ids.getValue(group.id).toString()
          val element = current.resolved.element(item.id)!!
          val target = face(item, dark)
          val slot = slots[glass to item.id]
          if (slot == null) {
            val enters = previous != null && previous!!.ids.values.any { it.toString() == glass }
            slots[glass to item.id] = Slot(glass, item.id, element.leftDp - group.leftDp, element.widthDp,
              if (enters) GlassContentJourney.rest(null).retarget(time, target) else GlassContentJourney.rest(target))
          } else {
            slot.x = element.leftDp - group.leftDp
            slot.w = element.widthDp
            slot.journey = slot.journey.retarget(time, target)
            if (glass in reshaped) slot.journey = slot.journey.soften(time)
            slot.attached = true
          }
        }
        for (slot in slots.values.filter { !it.attached }) {
          val group = current.resolved.groups.firstOrNull { current.ids[it.id].toString() == slot.group }
          val gone = previous?.resolved?.element(slot.id)
          if (group != null && gone != null) {
            slot.x = gone.leftDp - group.leftDp
            slot.journey = slot.journey.retarget(time, null)
          }
        }
        previous = current
      }
      driver.step(10_000_000_000L + glassTraceTimeNanos(index - 3, 120), current.plane, GlassPlaneMotion())
      val u = (time - current.input.at).toFloat() / GlassContentDefaults.DurationMs
      val travelling = if (driver.journey != null || u < 1f) current.transfers else emptyMap()
      val entering = if (driver.journey != null || u < GlassContentDefaults.EnterEnd) current.arrivals else emptyMap()
      val elements = frame.elements.map { raw ->
        val element = LinkedHashMap(raw)
        @Suppress("UNCHECKED_CAST")
        val layers = (raw["content"] as? List<Map<String, Any?>>).orEmpty()
        element["content"] = layers.flatMap { carrier ->
          val glass = carrier.getValue("name").toString().removePrefix("c-")
          val group = current.resolved.groups.firstOrNull { current.ids[it.id].toString() == glass }
          val oldGroup = prepared.lastOrNull { it.input.at <= time && it.ids.values.any { id -> id.toString() == glass } }
            ?.let { state -> state.resolved.groups.first { state.ids[it.id].toString() == glass } }
          val nominal = group ?: oldGroup ?: return@flatMap emptyList()
          val flying = driver.frame?.pieces?.firstOrNull { (it.flightId.targetKey ?: it.flightId.sourceKey) == glass }
          val place = Rect(nominal.leftDp * density, top * density,
            (nominal.leftDp + nominal.widthDp) * density, (top + metrics.buttonWidthDp) * density)
          val anchor = when (nominal.side) {
            FluidToolbarSide.Leading -> FluidGlassContentAnchor.Start
            FluidToolbarSide.Trailing -> FluidGlassContentAnchor.End
            FluidToolbarSide.Center -> FluidGlassContentAnchor.Center
          }
          val look = GlassContentLook.Sharp.copy(alpha = 0.999f)
          val centerFlight = flying?.let { fluidGlassContentFlightOf(it, GlassPieceContent.Rest, look, place, FluidGlassSwell.None) }
          val anchoredFlight = flying?.let {
            fluidGlassContentFlightOf(it, GlassPieceContent.Rest, look, place, FluidGlassSwell.None, anchor = anchor)
          }
          val shift = ((anchoredFlight?.offset?.x ?: 0f) - (centerFlight?.offset?.x ?: 0f)) / density
          slots.values.filter { it.group == glass && it.attached && it.id !in travelling && it.id !in entering }.flatMap { slot ->
            slot.journey.frame(time).layers.mapNotNull { layer ->
              val shown = layer.key as Face
              val alpha = number(carrier, "alpha", 1f) * layer.look.alpha
              if (alpha <= 0.001f) return@mapNotNull null
              val groupBlur = number(carrier, "blurDp")
              val ownBlur = glassContentBlurRadiusPx(glassContentSigmaPx(layer.look.blur, shown.size * density)) / density
              linkedMapOf<String, Any?>(
                "name" to "${slot.id}-${layer.id}", "x" to number(carrier, "x") + slot.x + shift,
                "y" to number(carrier, "y"), "w" to slot.w, "h" to metrics.buttonWidthDp, "color" to shown.color,
                "alpha" to alpha, "scale" to number(carrier, "scale", 1f) * layer.look.scale,
                "blurDp" to hypot(groupBlur, ownBlur), "marginDp" to 12,
              ).also { record ->
                record.putAll(shown.content())
                carrier["clip"]?.let { clip -> record["clip"] = clip }
              }
            }
          }
        }
        element
      }.toMutableList()
      val icons = travelling.map { (id, trip) ->
        val item = current.resolved.element(id)!!.item
        val glyph = face(item, dark)
        val flying = driver.frame?.pieces?.find { it.piece.id == trip.glass }
        val center = trip.center(trip.startDp * density, trip.endDp * density, flying?.bounds?.center?.x,
          flying?.bounds?.width?.div(density), flying?.phase ?: 1f) / density
        mapOf("name" to "$id-stabile", "x" to center - metrics.buttonWidthDp / 2f, "y" to top,
          "w" to metrics.buttonWidthDp, "h" to metrics.buttonWidthDp, "icon" to glyph.icon,
          "iconSizeDp" to glyph.size, "color" to glyph.color, "marginDp" to 12)
      }
      val fading = mutableListOf<Map<String, Any?>>()
      fun addFading(element: FluidToolbarElement, glass: FluidToolbarGlassId, role: GlassContentRole, clip: Boolean) {
        val alpha = GlassContentCurves.alpha(role, u, true)
        if (alpha <= 0.001f) return
        val glyph = face(element.item, dark)
        val bounds = driver.frame?.pieces?.find { it.piece.id == glass }?.bounds
        val group = current.resolved.groups.firstOrNull { it.id == element.groupId }
        val offset = if (role == GlassContentRole.Enter && bounds != null && group != null)
          bounds.center.x / density - group.leftDp - group.widthDp / 2f else 0f
        val blur = GlassContentCurves.blur(role, u) * GlassContentDefaults.PeakBlur
        val layer = linkedMapOf<String, Any?>("name" to "${element.item.id}-$role",
          "color" to glyph.color, "x" to element.leftDp + offset, "y" to top,
          "w" to element.widthDp, "h" to metrics.buttonWidthDp, "alpha" to alpha,
          "scale" to GlassContentCurves.scale(role, u),
          "blurDp" to glassContentBlurRadiusPx(glassContentSigmaPx(blur, glyph.size * density)) / density, "marginDp" to 12)
        if (clip && bounds != null) layer["clip"] = mapOf("x" to bounds.left / density, "y" to top,
          "w" to bounds.width / density, "h" to metrics.buttonWidthDp, "radius" to 0)
        layer.putAll(glyph.content())
        fading += layer
      }
      current.departures.forEach { ghost -> addFading(ghost.element, ghost.glass, GlassContentRole.Exit, ghost.glassStays) }
      entering.values.forEach { element -> addFading(element, current.ids.getValue(element.groupId!!), GlassContentRole.Enter, true) }
      // Gli elementi senza vetro (il conteggio al centro): la loro scritta, ferma, sullo scambio R7 del posto.
      if (icons.isNotEmpty() || fading.isNotEmpty())
        elements += linkedMapOf<String, Any?>("kind" to "shape", "name" to "icone", "x" to 0, "y" to 0, "w" to width,
          "h" to height, "radius" to 0, "color" to "#00000000", "contentClip" to "none", "content" to icons + fading)
      val flying = driver.frame?.pieces.orEmpty()
      val stats = current.plane.pieces.map { piece ->
        val rect = flying.firstOrNull { it.flightId.targetKey == piece.key }?.bounds ?: piece.bounds
        mapOf("name" to piece.key.toString(), "left" to rect.left / density, "right" to rect.right / density,
          "width" to rect.width / density, "center" to rect.center.x / density,
          "change" to (flying.firstOrNull { it.flightId.targetKey == piece.key }?.change?.name ?: "Rest"))
      } + flying.filter { f -> f.flightId.targetKey == null }.map { f ->
        // Chi se ne va (per lente o confluendo): conta anche lui, finché il piano lo disegna.
        mapOf("name" to f.flightId.sourceKey.toString(), "left" to f.bounds.left / density, "right" to f.bounds.right / density,
          "width" to f.bounds.width / density, "center" to f.bounds.center.x / density, "change" to f.change.name)
      }
      elements += linkedMapOf("kind" to "shape", "name" to "misure", "x" to 0, "y" to 0,
        "w" to 1, "h" to 1, "color" to "#00000000", "radius" to 0, "gruppi" to stats)
      GlassTraceFrame(time, elements)
    }
    val head = LinkedHashMap(base.head)
    head["_provenienza"] = "F6B.11: DSL (selection) -> fluidToolbarModel -> resolveFluidToolbar -> FluidToolbarMemory -> " +
      "FluidGlassPlaneDriver; F38aTraces per ricetta, GlassContentJourney per slot. Nessun Compose/GPU. Schema di F4aToolbarExportTest."
    head["_stati"] = inputs.map { mapOf("t" to it.at, "nome" to it.name) }
    return GlassTrace(name, "f6b-11", density, base.theme, width, height, frames, 120, head)
  }

  // --- tasto-diviso-menu e titolo-selettore -------------------------------------------------------------------------

  /** Il telefono di prova: 412 × 900 dp a densità 3; la tela è la parte alta, con la barra. */
  private val host = Size(412f, 900f)
  private val canvasOrigin = Offset(0f, 0f)
  private val canvasW = 412f
  private val canvasH = 420f

  private fun head(note: String, dark: Boolean): (Rect) -> Map<String, Any?> = { _ ->
    linkedMapOf(
      "_" to note,
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to if (dark) "#1C1C1E" else "#F2F2F7"),
      "page" to listOf(
        linkedMapOf("kind" to "background", "x" to 16, "y" to 110, "w" to 380, "h" to 300,
          "background" to linkedMapOf("kind" to "synthetic", "scaleDp" to 46, "detail" to 0.5,
            "palette" to if (dark) listOf("#2C2C2E", "#3A3A3C", "#1F3A5F", "#48484A") else
              listOf("#DCE7F5", "#F5E6D3", "#E9E9E9", "#D2D2D2"))),
      ),
      "events" to listOf(
        linkedMapOf("t" to 133.4, "kind" to "mark", "label" to "B1 a: pieno a ~133 ms"),
      ),
    )
  }

  private fun menuScene(
    name: String,
    anchor: Rect,
    faceText: String?,
    faceTextSizeDp: Float,
    trailing: String,
    items: List<MenuItemSpec>,
    note: String,
    dark: Boolean,
  ) = MenuScene(
    name = name, fps = 120, density = density, hostDp = host, anchorDp = anchor,
    canvasOriginDp = canvasOrigin, canvasWidthDp = canvasW, canvasHeightDp = canvasH,
    longPressMs = 0.0, preambleMs = 0.0, items = items,
    faceIcon = trailing, faceIconSizeDp = 15f, labelDpPerChar = 9f, maxMs = 1800.0, tailFrames = 6,
    head = head(note, dark), faceText = faceText, faceTextSizeDp = faceTextSizeDp,
    faceTrailingIcon = trailing.takeIf { faceText != null },
  )

  private fun menuTraces(scene: (Boolean) -> MenuScene): List<MenuBuilt> = listOf(false, true).flatMap { dark ->
    val s = scene(dark)
    val suffix = if (dark) "-scuro" else ""
    val background = linkedMapOf<String, Any?>("kind" to "color", "color" to if (dark) "#1C1C1E" else "#F2F2F7")
    listOf(
      buildMenuTrace(s, dress = TraceDress.Liquid, name = "${s.name}$suffix", phase = "f6b-11", dark = dark, unified = true,
        background = background, variant = MenuTraceVariant(pointOfLight = true, corrected = true)),
      buildMenuTrace(s, dress = TraceDress.Liquid, name = "${s.name}-congedo$suffix", phase = "f6b-11", dark = dark,
        unified = true, background = background,
        variant = MenuTraceVariant(corrected = true, closingAtMs = 900, alignToClose = true)).withoutEvents(),
    )
  }

  @Test fun `tasto-diviso-menu, il menu nasce dal vetro intero del tasto e ci rientra`() {
    // «Invia ▾» a destra nella barra di un telefono da 412 dp: la scritta (5 × 9 + 32) e il filo con la freccia (39).
    val w = 5 * 9f + 32f + FluidSplitButtonDefaults.Hairline.value + FluidSplitButtonDefaults.ArrowZone.value
    val capsule = Rect(412f - 16f - w, 32f + 6f, 412f - 16f, 32f + 6f + 46f)
    val built = menuTraces { dark ->
      menuScene("tasto-diviso-menu", capsule, "Invia", 17f, "fluid:keyboard_arrow_down|circle",
        listOf(MenuItemSpec("Programma invio", "fluid:schedule|circle"), MenuItemSpec("Invia come bozza", "fluid:drafts|circle")),
        "F6B.11: «Invia ▾», la freccia toccata. Il menu del tasto singolo (F5A.2, B1 a) dal vetro intero del tasto diviso; " +
          "numeri e orologio dell'engine (OggiMenuApertura.kt). La faccia del banco è la scritta con la freccia.", dark)
    }
    for (b in built) {
      write(b.trace)
      assertTrue("${b.trace.name}: la goccia", b.ringFrames > 0)
      assertTrue("${b.trace.name}: il pannello sotto il tasto", b.panelPx.top >= capsule.top * density)
    }
  }

  @Test fun `titolo-selettore, gli account nascono dal titolo e ci rientrano`() {
    // «Posta in arrivo» agganciato al centro della barra (21 dp di carattere), col disco (0,86 × 21) a 0,38 × 21: il menu
    // nasce dal quadrato di 44 dp attorno al disco (`fluidTitleSelectorAnchor`), come da un tasto tondo della barra.
    val text = 15 * 21f * 0.52f
    val reserve = 21f * FluidTitleSelectorDefaults.Reserve
    val left = (412f - text - reserve) / 2f
    val disc = Offset(left + text + 21f * FluidTitleSelectorDefaults.Gap + 21f * FluidTitleSelectorDefaults.Disc / 2f, 32f + 23f)
    val anchor = fluidTitleSelectorAnchor(disc, 21f * FluidTitleSelectorDefaults.Disc, 44f)
    val built = menuTraces { dark ->
      menuScene("titolo-selettore", anchor, null, 21f, "fluid:expand_more|circle",
        listOf(MenuItemSpec("Tutte le caselle", "fluid:inbox|circle"), MenuItemSpec("Lavoro", ""),
          MenuItemSpec("Personale", ""), MenuItemSpec("Aggiungi account", "fluid:add|circle", startsSection = true)),
        "F6B.11: il titolo agganciato toccato apre gli account. L'ancora Expand è il quadrato di 44 dp attorno al disco " +
          "(fluidTitleSelectorAnchor), il viaggio del menu dal tasto (F5A.2, B1 a). Nel banco la sorgente è disegnata come " +
          "un tasto di vetro a riposo; sul dispositivo il disco non ha vetro, e il titolo resta al suo posto.", dark)
    }
    for (b in built) {
      write(b.trace)
      assertTrue("${b.trace.name}: la goccia", b.ringFrames > 0)
    }
    assertEquals(4, built.size)
  }

  /** Il congedo parte a t = 0 dalla richiesta: i segni dell'apertura non sono suoi (come in `F6b8FilterTraces`). */
  private fun MenuBuilt.withoutEvents(): MenuBuilt {
    val t = trace
    val head = LinkedHashMap(t.head)
    head.remove("events")
    val stripped = GlassTrace(t.name, t.phase, t.density, t.theme, t.widthDp, t.heightDp, t.frames, t.fps, head)
    return MenuBuilt(stripped, panelPx, pressedPx, ringFrames, integratedFrames, openBounds, closeBounds, born, rowShift)
  }
}
