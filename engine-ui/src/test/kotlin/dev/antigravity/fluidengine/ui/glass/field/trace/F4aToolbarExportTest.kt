package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.page.FluidBarTitleSwap
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import java.io.File
import java.util.Locale
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

/**
 * F4A.5: dichiarazioni DSL, risolutore, memoria dei vetri e conducente del contenitore di produzione.
 * F38aTraces scrive il materiale dalla ricetta; qui si aggiungono i singoli slot col loro GlassContentJourney e
 * l'ancora laterale della barra. Nessuna curva di geometria o di sfocatura riscritta per il banco.
 */
class F4aToolbarExportTest {
  private val width = 400f
  private val height = 108f
  private val top = 32f
  private val density = 3f
  private val noop: () -> Unit = {}

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

  private fun browse() = buildFluidToolbar(null) {
    button("lead", FluidIcons.FormatListBulleted, "Elenco", placement = FluidToolbarPlacement.Leading, onClick = noop)
    button("heart", FluidIcons.Favorite, "Preferito", onClick = noop)
    button("share", FluidIcons.IosShare, "Condividi", onClick = noop)
    button("add", FluidIcons.Add, "Aggiungi", onClick = noop)
  }
  private fun compact() = buildFluidToolbar(null) {
    button("lead", FluidIcons.Search, "Cerca", placement = FluidToolbarPlacement.Leading, onClick = noop)
    button("add", FluidIcons.Add, "Aggiungi", onClick = noop)
  }
  private fun edit() = buildFluidToolbar(null) {
    close(noop, id = "lead")
    button("share", FluidIcons.IosShare, "Condividi", onClick = noop)
    button("delete", FluidIcons.Delete, "Elimina", onClick = noop)
    spacer()
    button("check", FluidIcons.Check, "Conferma neutra", onClick = noop)
  }
  private fun primary(prominent: Boolean) = buildFluidToolbar(null) {
    button("share", FluidIcons.IosShare, "Condividi", onClick = noop)
    if (prominent) primary("heart", FluidIcons.Favorite, "Preferito", onClick = noop)
    else button("heart", FluidIcons.Favorite, "Preferito", onClick = noop)
  }
  private fun context(expanded: Boolean) = buildFluidToolbar(null) {
    button("a", if (expanded) FluidIcons.AttachFile else FluidIcons.Folder, "Primo",
      placement = FluidToolbarPlacement.Leading, onClick = noop)
    button("b", if (expanded) FluidIcons.Search else FluidIcons.Delete, "Secondo",
      placement = FluidToolbarPlacement.Leading, onClick = noop)
    if (expanded) button("c", FluidIcons.Add, "Aggiungi", placement = FluidToolbarPlacement.Leading, onClick = noop)
    if (expanded) button("send", FluidIcons.ArrowUpward, "Invia", tint = Color(0xFF007AFF), onClick = noop)
    else {
      button("reply", FluidIcons.IosShare, "Rispondi", onClick = noop)
      button("send", FluidIcons.Send, "Invia", onClick = noop)
    }
  }
  private fun page(child: Int) = buildFluidToolbar(noop) {
    if (child > 0) {
      group("pager") {
        button("up", FluidIcons.ExpandLess, "Precedente", onClick = noop)
        button("down", FluidIcons.ExpandMore, "Successiva", onClick = noop)
      }
      spacer()
    }
    button("trail", if (child == 0) FluidIcons.FilterList else FluidIcons.MoreHoriz,
      "Opzioni", onClick = noop)
  }

  private fun prepare(inputs: List<Input>, bar: FluidToolbarBar = FluidToolbarBar.Top): List<Prepared> {
    val metrics = if (bar == FluidToolbarBar.Bottom) fluidBottomBarMetrics() else fluidToolbarMetrics()
    val memory = FluidToolbarMemory()
    var previousAt = Double.NEGATIVE_INFINITY
    return inputs.map { input ->
      if (input.at - previousAt >= 1000.0) {
        memory.finishTransfers(memory.transfers)
        memory.ghosts.toList().forEach(memory::forget)
        memory.arrivals.values.toList().forEach(memory::finishArrival)
      }
      previousAt = input.at
      val model = fluidToolbarModel(input.declaration, textWidthDp = {
        require(it == "Bozza")
        78f // larghezza della scritta con padding in questa scena a densità 3
      },
        tintOf = { fluidToolbarTint(it, Color(0xFF007AFF), Color(0xFFFF3B30)) })
      val resolved = resolveFluidToolbar(model, width, metrics = metrics, bar = bar)
      assertTrue("${input.name}: capienza", resolved.fits)
      // In basso il tasto che resta al suo posto va col bordo del suo vetro (scelte-b): lo stesso interruttore della barra.
      memory.update(resolved, FluidToolbarMotion.Full, ridesEdge = bar == FluidToolbarBar.Bottom)
      val pieces = resolved.groups.map { group ->
        val id = memory.glassIds.getValue(group.id)
        val bounds = Rect(group.leftDp * density, top * density,
          (group.leftDp + group.widthDp) * density, (top + metrics.buttonWidthDp) * density)
        GlassPlanePiece(id.toString(), bounds, FluidCornerRadii.all(bounds.height / 2), id = id,
          transition = if (fluidToolbarGlassTransition(group) == FluidGlassTransition.MaterializeWhenAlone)
            GlassPlaneTransition.MaterializeWhenAlone else GlassPlaneTransition.MatchedGeometry,
          // F4A.5b: ciò che il vetro porta, come lo dichiara FluidToolbarGroupPiece (il tondo che cambia simbolo respira).
          contentKey = fluidToolbarGlassContentKey(group))
      }
      Prepared(input, resolved, memory.glassIds.toMap(), GlassPlaneState(pieces, 16f * density,
        Rect(0f, 0f, width * density, height * density)), memory.transfers.toMap(), memory.ghosts.toList(), memory.arrivals.toMap())
    }
  }

  private fun face(item: FluidToolbarItem, dark: Boolean): Face {
    val vector = if (item.role == FluidToolbarRole.Overflow) FluidIcons.MoreHoriz else item.symbol as? ImageVector
    val icon = vector?.name?.substringAfter('.')?.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")?.lowercase(Locale.ROOT)
    return Face(icon?.let { "fluid:$it" }, item.label, if (item.isPrimary) "#FFFFFF" else if (item.selected) "#007AFF" else item.tintArgb?.let { String.format(Locale.ROOT, "#%06X", it and 0xFFFFFFL) }
      ?: if (dark) "#F2F2F7" else "#17171A",
      fluidToolbarIconSize(item.role).value)
  }

  private fun trace(
    name: String,
    inputs: List<Input>,
    last: Int,
    dark: Boolean = false,
    pages: Boolean = false,
    /** La variante del contenuto che resta in un vetro che cambia forma (F4A.5b, `barkeep`): qui nitido, per tenere la traccia di confronto di F4A.5; di serie
     * e' morbido (scelta di Alessio del 17/11/2026), la traccia `barra-tre-stati-morbido`. */
    kept: FluidToolbarKeptContent = FluidToolbarKeptContent.Sharp,
    oldSlots: Boolean = false,
    phase: String = "f4a",
    bar: FluidToolbarBar = FluidToolbarBar.Top,
  ): GlassTrace {
    val prepared = prepare(inputs, bar)
    val metrics = prepared.first().resolved.metrics
    val neutral = GlassTint(Color.Transparent, Color.Transparent, Color.Transparent, GlassTintMode.Neutral)
    val tints = prepared.flatMap { state -> state.resolved.groups.map { group ->
      val primary = group.items.singleOrNull()?.takeIf { it.isPrimary }
      state.ids.getValue(group.id).toString() to if (primary == null) neutral else
        fluidGlassTint(FluidGlass.Regular.tint(Color(primary.tintArgb ?: 0xFF007AFF)), neutral)
    } }.toMap()
    val labels = prepared.flatMap { it.plane.pieces }.associate { it.key to F38aTraces.Label(icon = "plus") }
    val films = HashMap<Double, Map<String, Float?>>()
    val base = F38aTraces.build(F38aTraces.Scene(name, "barra dal modello Kotlin, contenitore e contenuto veri",
      width, height, dark, prepared.first().plane, prepared.drop(1).map { F38aTraces.Step(it.input.at, it.plane) },
      labels, last, density = density, spacingDp = 16f, phase = phase,
      background = mapOf("kind" to "color", "color" to if (dark) "#24252A" else "#C4C7CE"),
      tintOf = { tints[it.key] ?: neutral })) { time, journey ->
      films[time] = tints.keys.associateWith(journey::filmAlpha)
    }
    val slots = LinkedHashMap<Pair<String, Any>, Slot>()
    val title = FluidBarTitleSwap(blurAvailable = true)
    var previous: Prepared? = null
    var previousTime = base.frames.first().tMs
    val driver = FluidGlassPlaneDriver()
    val frames = base.frames.mapIndexed { index, frame ->
      val time = frame.tMs
      val current = prepared.last { it.input.at <= time + 0.00001 }
      val changed = previous !== current
      title.advance(time - previousTime)
      previousTime = time
      if (changed) {
        if (pages) title.retarget(current.input.name, if (current.input.name == "elenco") -1 else 1, snap = false)
        slots.values.forEach { it.attached = false }
        // I vetri che cambiano forma (F4A.5b): lo stesso vetro con altri tasti. È il cambio di `fluidToolbarSlotShapeKey`.
        val old = previous
        val reshaped = if (old == null) emptySet() else current.resolved.groups.mapNotNullTo(HashSet()) { group ->
          val glass = current.ids.getValue(group.id)
          val before = old.resolved.groups.firstOrNull { old.ids[it.id] == glass } ?: return@mapNotNullTo null
          glass.toString().takeIf {
            fluidToolbarSlotShapeKey(before, kept) != fluidToolbarSlotShapeKey(group, kept)
          }
        }
        for (group in current.resolved.groups) for (item in group.items) {
          val glass = current.ids.getValue(group.id).toString()
          val element = current.resolved.element(item.id)!!
          val slotKey = glass to item.id
          val target = face(item, dark)
          val slot = slots[slotKey]
          if (slot == null) {
            val enters = previous != null && previous!!.ids.values.any { it.toString() == glass }
            slots[slotKey] = Slot(glass, item.id, element.leftDp - group.leftDp, element.widthDp,
              if (enters) GlassContentJourney.rest(null).retarget(time, target) else GlassContentJourney.rest(target))
          } else {
            slot.x = element.leftDp - group.leftDp
            slot.w = element.widthDp
            slot.journey = slot.journey.retarget(time, target)
            // La variante «morbida»: il contenuto che resta nel vetro che cambia forma si ammorbidisce, come in produzione.
            if (glass in reshaped) slot.journey = slot.journey.soften(time)
            slot.attached = true
          }
        }
        // I fantasmi di F4A.2: restano nella posizione di prima rispetto al vetro d'arrivo.
        for (slot in slots.values.filter { !it.attached }) {
          val group = current.resolved.groups.firstOrNull { current.ids[it.id].toString() == slot.group }
          val old = previous?.resolved?.element(slot.id)
          if (group != null && old != null) {
            slot.x = old.leftDp - group.leftDp
            slot.journey = slot.journey.retarget(time, null)
          }
        }
        previous = current
      }
      driver.step(10_000_000_000L + glassTraceTimeNanos(index - 3, 120), current.plane, GlassPlaneMotion())
      val elapsed = (time - current.input.at).toFloat()
      val u = elapsed / GlassContentDefaults.DurationMs
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
            ?.let { old -> old.resolved.groups.first { old.ids[it.id].toString() == glass } }
          val nominal = group ?: oldGroup ?: return@flatMap emptyList()
          val flying = driver.frame?.pieces?.firstOrNull { (it.flightId.targetKey ?: it.flightId.sourceKey) == glass }
          // L'ancora modifica la sola traslazione, come fluidGlassContentFlightOf: mai scala per la larghezza.
          val place = Rect(nominal.leftDp * density, top * density,
            (nominal.leftDp + nominal.widthDp) * density, (top + metrics.buttonWidthDp) * density)
          val anchor = when (nominal.side) {
            FluidToolbarSide.Leading -> FluidGlassContentAnchor.Start
            FluidToolbarSide.Trailing -> FluidGlassContentAnchor.End
            FluidToolbarSide.Center -> FluidGlassContentAnchor.Center
          }
          val look = GlassContentLook.Sharp.copy(alpha = 0.999f) // valuta anche il volo fermo, senza il fast path null.
          val centerFlight = flying?.let { fluidGlassContentFlightOf(it, GlassPieceContent.Rest, look,
            place, FluidGlassSwell.None) }
          val anchoredFlight = flying?.let { fluidGlassContentFlightOf(it, GlassPieceContent.Rest, look,
            place, FluidGlassSwell.None, anchor = anchor) }
          val shift = ((anchoredFlight?.offset?.x ?: 0f) - (centerFlight?.offset?.x ?: 0f)) / density
          slots.values.filter { it.group == glass && (oldSlots || it.attached && it.id !in travelling && it.id !in entering) }.flatMap { slot ->
            slot.journey.frame(time).layers.mapNotNull { layer ->
              val shown = layer.key as Face
              val alpha = number(carrier, "alpha", 1f) * layer.look.alpha
              if (alpha <= 0.001f) return@mapNotNull null
              val groupBlur = number(carrier, "blurDp")
              val ownBlur = glassContentBlurRadiusPx(glassContentSigmaPx(layer.look.blur, shown.size * density)) / density
              linkedMapOf<String, Any?>(
                "name" to "${slot.id}-${layer.id}", "x" to number(carrier, "x") + slot.x + shift,
                "y" to number(carrier, "y"), "w" to slot.w, "h" to metrics.buttonWidthDp,
                "color" to shown.color,
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
      if (!oldSlots) {
        val icons = travelling.map { (id, trip) ->
          val item = current.resolved.element(id)!!.item
          val face = face(item, dark)
          val flying = driver.frame?.pieces?.find { it.piece.id == trip.glass }
          val shift = flying?.let { trip.ride?.shiftPx(it.bounds.left, it.bounds.right, width, density, rtl = false) } ?: 0f
          val center = trip.center(trip.startDp * density, trip.endDp * density, flying?.bounds?.center?.x,
            flying?.bounds?.width?.div(density), flying?.phase ?: 1f, shift) / density
          val blur = if (trip.softened && kept == FluidToolbarKeptContent.Soft && driver.journey != null)
            GlassContentCurves.softBlur((driver.elapsedMs / GlassContentDefaults.DurationMs).toFloat()) *
              GlassContentDefaults.PeakBlur * GlassContentDefaults.KeptSoftness else 0f
          val inkPhase = if (trip.changesInkInPlace(item))
            ((time - current.input.at) / GlassContentDefaults.DurationMs).toFloat() else flying?.phase ?: 1f
          val tone = androidx.compose.ui.graphics.lerp(Color(0xff000000L or face(trip.startItem, dark).color.drop(1).toLong(16)),
            Color(0xff000000L or face.color.drop(1).toLong(16)),
            trip.inkFraction(item, flying?.let { films[time]?.get(trip.glass.toString()) }, inkPhase))
          mapOf("name" to "$id-stabile", "x" to center - metrics.buttonWidthDp / 2f, "y" to top,
            "w" to metrics.buttonWidthDp, "h" to metrics.buttonWidthDp, "icon" to face.icon,
            "iconSizeDp" to face.size, "color" to glassColor(tone),
            "blurDp" to glassContentBlurRadiusPx(glassContentSigmaPx(blur, face.size * density)) / density, "marginDp" to 12)
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
        current.departures.forEach { ghost ->
          addFading(ghost.element, ghost.glass, GlassContentRole.Exit, ghost.glassStays)
        }
        entering.values.forEach { element ->
          addFading(element, current.ids.getValue(element.groupId!!), GlassContentRole.Enter, true)
        }
        if (icons.isNotEmpty() || fading.isNotEmpty())
        elements += linkedMapOf<String, Any?>("kind" to "shape", "name" to "icone", "x" to 0,
          "y" to 0, "w" to width, "h" to height, "radius" to 0, "color" to "#00000000",
          "contentClip" to "none", "content" to icons + fading)
      }
      // Queste scene usano vetro neutro; la freccia del contesto ha la sola tinta del contenuto.
      // Il titolo è contenuto senza vetro, guidato dallo stesso FluidBarTitleSwap dell'host.
      if (pages) elements += linkedMapOf<String, Any?>("kind" to "shape", "name" to "titolo", "x" to 0,
        "y" to top, "w" to width, "h" to metrics.buttonWidthDp, "radius" to 0, "color" to "#00000000", "contentClip" to "none",
        "content" to prepared.map { it.input.name }.distinct().mapNotNull { page ->
          val alpha = title.alpha(page)
          if (alpha <= 0.001f) null else mapOf("name" to page, "x" to 120f + title.step(page) * FluidPageMotion.BarTitleStepDp,
            "y" to 0, "w" to 160, "h" to metrics.buttonWidthDp, "label" to if (page == "elenco") "Viaggi" else "Viaggio",
            "labelSizeDp" to 17, "labelWeight" to 600, "alpha" to alpha,
            "blurDp" to glassContentBlurRadiusPx(title.blur(page) * 17f), "marginDp" to 12,
            "color" to if (dark) "#F2F2F7" else "#17171A")
        })
      val flying = driver.frame?.pieces.orEmpty()
      val stats = current.plane.pieces.map { piece ->
        val rect = flying.firstOrNull { it.flightId.targetKey == piece.key }?.bounds ?: piece.bounds
        mapOf("name" to piece.key.toString(), "left" to rect.left / density, "right" to rect.right / density,
          "width" to rect.width / density, "center" to rect.center.x / density,
          "change" to (flying.firstOrNull { it.flightId.targetKey == piece.key }?.change?.name ?: "Rest"))
      }
      elements += linkedMapOf("kind" to "shape", "name" to "misure", "x" to 0, "y" to 0,
        "w" to 1, "h" to 1, "color" to "#00000000", "radius" to 0, "gruppi" to stats)
      GlassTraceFrame(time, elements)
    }
    val firstKeys = prepared.first().plane.pieces.map { it.key.toString() }
    val head = LinkedHashMap(base.head)
    head["_provenienza"] = "DSL -> fluidToolbarModel -> resolveFluidToolbar -> FluidToolbarMemory -> FluidGlassPlaneDriver; F38aTraces per ricetta, GlassContentJourney per slot, FluidBarTitleSwap per titolo. Nessun Compose/GPU."
    head["_stati"] = inputs.map { mapOf("t" to it.at, "nome" to it.name) }
    head["probes"] = firstKeys.flatMap { key -> listOf("left", "right", "width", "center").map { field ->
      mapOf("name" to "$key-$field", "kind" to "value", "path" to "misure.gruppi[$key].$field", "unit" to "dp")
    } }
    return GlassTrace(name, phase, density, base.theme, width, height, frames, 120, head)
  }

  private fun number(map: Map<String, Any?>, key: String, default: Float = 0f) = (map[key] as? Number)?.toFloat() ?: default

  private fun traces(): List<GlassTrace> {
    val three = listOf(Input(-100.0, "sfoglia", browse()), Input(0.0, "seleziona-compatta", compact()),
      Input(1000.0, "modifica", edit()), Input(2000.0, "sfoglia", browse()))
    return listOf(trace("barra-tre-stati", three, 360), trace("barra-tre-stati-scuro", three, 360, dark = true),
      trace("barra-tre-stati-morbido", three, 360, kept = FluidToolbarKeptContent.Soft),
      trace("barra-contesto", listOf(Input(-100.0, "prima", context(false)), Input(0.0, "dopo", context(true))), 144),
      trace("barra-fra-pagine", listOf(Input(-100.0, "elenco", page(0)), Input(0.0, "dettaglio", page(1)),
        Input(1500.0, "elenco", page(0))), 360, pages = true),
      trace("barra-contesto-interrotta", listOf(Input(-100.0, "prima", context(false)), Input(0.0, "dopo", context(true)),
        Input(150.0, "ripensamento", context(false))), 144),
      trace("barra-principale", listOf(Input(-100.0, "gruppo", primary(false)), Input(0.0, "principale", primary(true)),
        Input(1200.0, "gruppo", primary(false))), 300),
      trace("barra-principale-scuro", listOf(Input(-100.0, "gruppo", primary(false)), Input(0.0, "principale", primary(true)),
        Input(1200.0, "gruppo", primary(false))), 300, dark = true))
  }

  @Test fun `Gruppo Modifica e Allegato attraversano la finestra R7`() {
    val group = buildFluidToolbar(noop) {
      button("lead", FluidIcons.Edit, "Modifica", onClick = noop)
      button("mid", FluidIcons.IosShare, "Condividi", onClick = noop)
      button("trail", FluidIcons.Favorite, "Preferito", onClick = noop)
    }
    val edit = buildFluidToolbar(null) {
      close(noop, id = FluidToolbarIds.Back)
      text("draft", "Bozza", onClick = noop)
      confirm(noop)
      overflow { button("archive", FluidIcons.Archive, "Archivia", onClick = noop) }
    }
    val attachment = buildFluidToolbar(noop) {
      button("lead", FluidIcons.Edit, "Modifica", onClick = noop)
      spacer()
      button("trail", FluidIcons.Favorite, "Preferito", onClick = noop)
    }
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4A5_DIR"))
    for ((name, end) in listOf("gruppo-modifica" to edit, "gruppo-allegato" to attachment)) {
      val result = trace(name, listOf(Input(-100.0, "Gruppo", group), Input(0.0, name, end)), 122,
        kept = FluidToolbarKeptContent.Soft, phase = "f4a-corr")
      fun faces(time: Double) = result.frames.minBy { kotlin.math.abs(it.tMs - time) }.elements.flatMap { pane ->
        (pane["content"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
      }
      val old = faces(0.0).single { it["name"] == "mid-Exit" }
      for (ms in listOf(8.33, 25.0, 50.0, 100.0, 150.0)) {
        val share = faces(ms).single { it["name"] == "mid-Exit" }
        assertTrue((share["alpha"] as Number).toFloat() > 0f)
        assertEquals(old["x"], share["x"])
      }
      if (name == "gruppo-modifica") {
        val draft = faces(150.0).single { it["label"] == "Bozza" }
        assertTrue((draft["alpha"] as Number).toFloat() > 0f)
      }
      output?.let { result.write(File(it, "correzione/$name/trace.json")) }
    }
  }

  @Test fun sameIconsStayUniqueAcrossS6GroupChanges() {
    fun scene(name: String) = buildFluidToolbar(null) {
      when (name) {
        "gruppo-base", "principale" -> {
          button("edit", FluidIcons.Edit, "Modifica", onClick = noop)
          button("share", FluidIcons.IosShare, "Condividi", onClick = noop)
          if (name == "principale") primary("star", FluidIcons.Star, "Preferito", onClick = noop)
          else button("star", FluidIcons.Star, "Preferito", onClick = noop)
        }
        "corsivo", "grassetto" -> {
          group("format") {
            button("bold", FluidIcons.FormatBold, "Grassetto", onClick = noop, selected = name == "grassetto")
            button("italic", FluidIcons.FormatItalic, "Corsivo", onClick = noop, selected = name == "corsivo")
            button("underline", FluidIcons.FormatUnderlined, "Sottolineato", onClick = noop)
          }
          confirm(onClick = noop)
        }
        "note", "nota" -> {
          if (name == "nota") {
            button("lead", FluidIcons.IosShare, "Condividi", onClick = noop)
            spacer()
          }
          button("trail", FluidIcons.MoreHoriz, "Opzioni", onClick = noop)
        }
        else -> {
          button("lead", FluidIcons.Edit, "Modifica", onClick = noop)
          if (name == "allegato") spacer() else button("mid", FluidIcons.IosShare, "Condividi", onClick = noop)
          button("trail", FluidIcons.Favorite, "Preferito", onClick = noop)
        }
      }
    }
    val export = GlassTraceOutput.requestedDir(System.getenv("GLASS_F3_RIF4_DIR"))
    for ((name, sequence) in mapOf("principale" to listOf("gruppo-base", "principale", "gruppo-base"),
      "allegato-gruppo" to listOf("allegato", "gruppo"), "note-nota" to listOf("note", "nota"),
      "tinta" to listOf("corsivo", "grassetto", "corsivo"))) {
      val inputs = sequence.mapIndexed { i, page -> Input(listOf(-100.0, 0.0, 1200.0)[i], page, scene(page)) }
      val last = if (sequence.size == 3) 260 else 128
      val result = trace(name, inputs, last, kept = FluidToolbarKeptContent.Soft, phase = "f3-rif4")
      val placements = prepare(inputs).map { it.resolved }
      fun icons(frame: GlassTraceFrame) = frame.elements.flatMap { pane ->
        (pane["content"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty().map {
          it + ("absoluteX" to number(pane, "x") + (it["x"] as Number).toFloat())
        }
      }
      val keptIds = when (name) {
        "principale" -> listOf("edit", "share", "star")
        "allegato-gruppo" -> listOf("lead", "trail")
        "tinta" -> listOf("bold", "italic", "underline")
        else -> listOf("trail")
      }
      fun x(icon: Map<*, *>) = (icon["absoluteX"] as Number).toFloat() + (icon["w"] as Number).toFloat() / 2f
      for (frame in result.frames.filter { it.tMs >= 0 }) for (id in keptIds) {
        val glyph = icons(frame).single { it["name"].toString().startsWith("$id-") }
        assertEquals(1, icons(frame).count { it["icon"] == glyph["icon"] })
        if (name == "note-nota" || name == "tinta") assertEquals("$name, $id a ${frame.tMs} ms: $glyph",
          placements.first().element(id)!!.centerDp, x(glyph), 0.001f)
        if (name == "principale" && id != "star") {
          val ends = placements.map { it.element(id)!!.centerDp }
          assertTrue("salto di $id a ${frame.tMs}", x(glyph) in (ends.min() - .001f)..(ends.max() + .001f))
        }
      }
      if (name == "allegato-gruppo") {
        val opening = result.frames.minBy { kotlin.math.abs(it.tMs - 158.33) }
        val lead = icons(opening).single { it["name"].toString().startsWith("lead-") }
        val incoming = icons(opening).single { it["icon"] == "fluid:ios_share" }
        assertTrue("separazione fra Modifica e Condividi", x(incoming) - x(lead) >= 26f)
      }
      if (name == "tinta") {
        fun rgba(frame: GlassTraceFrame, id: String): String {
          val color = icons(frame).single { it["name"].toString().startsWith("$id-") }["color"].toString()
          return if (color.length == 7) color + "FF" else color
        }
        val first = result.frames.minBy { kotlin.math.abs(it.tMs) }
        assertEquals("#17171AFF", rgba(first, "bold"))
        val middle = result.frames.minBy { kotlin.math.abs(it.tMs - 800.0) }
        assertEquals("#007AFFFF", rgba(middle, "bold"))
        assertEquals("#17171AFF", rgba(middle, "italic"))
      }
      if (name == "principale") {
        val returning = result.frames.minBy { kotlin.math.abs(it.tMs - 1366.67) }
        val star = icons(returning).single { it["name"].toString().startsWith("star-") }
        val red = star["color"].toString().substring(1, 3).toInt(16)
        assertTrue("inchiostro ancora chiaro sul vetro quasi neutro: $star", red < 75)
      }
      export?.let {
        result.write(File(it, "$name/trace.json"))
        trace(name, inputs, last, kept = FluidToolbarKeptContent.Soft, oldSlots = true)
          .write(File(it, "slot-precedenti/$name/trace.json"))
      }
    }
  }

  @Test fun bottomGroupsUseTheSameClockAcrossPagesAndWhenThePlusSeparates() {
    fun scene(photo: Boolean = false, split: Boolean = false) = buildFluidToolbar(null) {
      group("comandi", placement = FluidToolbarPlacement.BottomBar) {
        button("elenco", if (photo) FluidIcons.Search else FluidIcons.FormatListBulleted, "Elenco", onClick = noop)
        flexibleSpacer()
        if (!photo) {
          button("cuore", FluidIcons.Favorite, "Preferito", onClick = noop)
          button("condividi", FluidIcons.IosShare, "Condividi", onClick = noop)
          if (split) spacer()
        }
        button("aggiungi", FluidIcons.Add, "Cambia gruppo", onClick = noop)
      }
    }
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4B1_DIR"))
    for ((name, other) in mapOf("capsula-cambia" to scene(photo = true), "separa-riunisci" to scene(split = true))) {
      val inputs = listOf(Input(-100.0, "raccolta", scene()), Input(0.0, "cambio", other), Input(900.0, "ritorno", scene()))
      val result = trace(name, inputs, 222, kept = FluidToolbarKeptContent.Soft, phase = "f4b-1", bar = FluidToolbarBar.Bottom)
      val poses = prepare(inputs, FluidToolbarBar.Bottom)
      assertEquals(144f, poses.first().resolved.groups.last().widthDp, 0f)
      assertEquals(48f, poses[1].resolved.groups.last().widthDp, 0f)
      assertEquals(poses.first().resolved, poses.last().resolved)
      assertEquals(2, poses.first().ids.size)
      assertEquals(if (name == "separa-riunisci") 3 else 2, poses[1].ids.size)
      assertTrue(result.frames.all { it.elements.isNotEmpty() })
      // Il «+»: in Foto va col bordo destro del suo vetro e torna (scelte-b, Apple 219 f8551: +0,16 H a 300 ms, di nuovo
      // al posto a ~490); quando si stacca in un vetro suo resta fermo (il bordo non è più lo stesso vetro).
      val rest = poses.first().resolved.element("aggiungi")!!.centerDp
      val h = poses.first().resolved.metrics.buttonWidthDp
      fun plus(ms: Double): Float {
        val frame = result.frames.minBy { kotlin.math.abs(it.tMs - ms) }
        val glyph = frame.elements.filter { it["name"] == "icone" }.flatMap { (it["content"] as List<*>).filterIsInstance<Map<*, *>>() }
          .singleOrNull { it["name"] == "aggiungi-stabile" } ?: return 0f // a riposo il più è nel suo posto
        return (glyph["x"] as Number).toFloat() + (glyph["w"] as Number).toFloat() / 2f - rest
      }
      if (name == "capsula-cambia") {
        assertEquals("fermo finché il vetro non parte", 0f, plus(50.0), 0.02f * h)
        assertEquals("a 300 ms a destra come Apple", 0.161f * h, plus(300.0), 0.03f * h)
        assertEquals("di nuovo al posto verso 490 ms", 0f, plus(490.0), 0.03f * h)
        assertEquals("fermo al posto alla fine", 0f, plus(850.0), 0.02f)
        assertEquals("nel ritorno va a sinistra", -0.155f * h, plus(1200.0), 0.03f * h)
        assertEquals("e torna", 0f, plus(1800.0), 0.02f)
      } else {
        for (ms in listOf(0.0, 150.0, 300.0, 450.0, 850.0)) assertEquals("staccato a $ms ms", 0f, plus(ms), 0.001f)
      }
      output?.let { result.write(File(it, "$name/trace.json")) }
    }
  }

  @Test fun `esporta le otto barre dal Kotlin di produzione e conserva le ventitre tracce del piano`() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4A5_DIR"))
    val all = traces()
    assertEquals(8, all.size)
    for (trace in all) {
      assertTrue(trace.frames.all { it.elements.isNotEmpty() })
      output?.let { trace.write(File(it, "${trace.name}/trace.json")) }
    }
    val parity = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4A5_PARITY_DIR"))
    if (parity != null) for (scene in F38aTraces.scenes() + F38aTraces.appleScenes() + F38aTraces.f310Scenes()) {
      F38aTraces.build(scene).write(File(parity, "${scene.name}/trace.json"))
    }
  }
}
