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
  private val metrics = fluidToolbarMetrics()
  private val noop: () -> Unit = {}

  private data class Input(val at: Double, val name: String, val declaration: FluidToolbarDeclaration)
  private data class Prepared(val input: Input, val resolved: FluidToolbarResolved,
    val ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>, val plane: GlassPlaneState)
  private data class Face(val icon: String, val color: String, val size: Float)
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

  private fun prepare(inputs: List<Input>): List<Prepared> {
    val memory = FluidToolbarMemory()
    return inputs.map { input ->
      val model = fluidToolbarModel(input.declaration, textWidthDp = { error("Nessun testo nelle scene") },
        tintOf = { fluidToolbarTint(it, Color(0xFF007AFF), Color(0xFFFF3B30)) })
      val resolved = resolveFluidToolbar(model, width, metrics = metrics)
      assertTrue("${input.name}: capienza", resolved.fits)
      memory.update(resolved, FluidToolbarMotion.Full)
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
        Rect(0f, 0f, width * density, height * density)))
    }
  }

  private fun face(item: FluidToolbarItem, dark: Boolean): Face {
    val icon = (item.symbol as ImageVector).name.substringAfter('.')
      .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase(Locale.ROOT)
    return Face("fluid:$icon", item.tintArgb?.let { String.format(Locale.ROOT, "#%06X", it and 0xFFFFFFL) }
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
  ): GlassTrace {
    val prepared = prepare(inputs)
    val labels = prepared.flatMap { it.plane.pieces }.associate { it.key to F38aTraces.Label(icon = "plus") }
    val base = F38aTraces.build(F38aTraces.Scene(name, "barra dal modello Kotlin, contenitore e contenuto veri",
      width, height, dark, prepared.first().plane, prepared.drop(1).map { F38aTraces.Step(it.input.at, it.plane) },
      labels, last, density = density, spacingDp = 16f, phase = "f4a",
      background = mapOf("kind" to "color", "color" to if (dark) "#24252A" else "#C4C7CE")))
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
          slots.values.filter { it.group == glass }.flatMap { slot ->
            slot.journey.frame(time).layers.mapNotNull { layer ->
              val shown = layer.key as Face
              val alpha = number(carrier, "alpha", 1f) * layer.look.alpha
              if (alpha <= 0.001f) return@mapNotNull null
              val groupBlur = number(carrier, "blurDp")
              val ownBlur = glassContentBlurRadiusPx(glassContentSigmaPx(layer.look.blur, shown.size * density)) / density
              linkedMapOf<String, Any?>(
                "name" to "${slot.id}-${layer.id}", "x" to number(carrier, "x") + slot.x + shift,
                "y" to number(carrier, "y"), "w" to slot.w, "h" to metrics.buttonWidthDp,
                "icon" to shown.icon, "iconSizeDp" to shown.size, "color" to shown.color,
                "alpha" to alpha, "scale" to number(carrier, "scale", 1f) * layer.look.scale,
                "blurDp" to hypot(groupBlur, ownBlur), "marginDp" to 12,
              ).also { carrier["clip"]?.let { clip -> it["clip"] = clip } }
            }
          }
        }
        element
      }.toMutableList()
      // Queste scene usano vetro neutro; la freccia del contesto ha la sola tinta del contenuto.
      // Il titolo è contenuto senza vetro, guidato dallo stesso FluidBarTitleSwap dell'host.
      if (pages) elements += linkedMapOf<String, Any?>("kind" to "shape", "name" to "titolo", "x" to 0,
        "y" to top, "w" to width, "h" to 44, "radius" to 0, "color" to "#00000000", "contentClip" to "none",
        "content" to prepared.map { it.input.name }.distinct().mapNotNull { page ->
          val alpha = title.alpha(page)
          if (alpha <= 0.001f) null else mapOf("name" to page, "x" to 120f + title.step(page) * FluidPageMotion.BarTitleStepDp,
            "y" to 0, "w" to 160, "h" to 44, "label" to if (page == "elenco") "Viaggi" else "Viaggio",
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
    return GlassTrace(name, "f4a", density, base.theme, width, height, frames, 120, head)
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
        Input(150.0, "ripensamento", context(false))), 144))
  }

  @Test fun `esporta le sei barre dal Kotlin di produzione e conserva le ventitre tracce del piano`() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4A5_DIR"))
    val all = traces()
    assertEquals(6, all.size)
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
