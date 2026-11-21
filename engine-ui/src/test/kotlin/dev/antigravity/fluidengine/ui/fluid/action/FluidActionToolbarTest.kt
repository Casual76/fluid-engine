package dev.antigravity.fluidengine.ui.fluid.action

import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.Save
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarContentChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarElementChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarGroupChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarItem
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKind
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKinds
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMemory
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMotion
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarRole
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.diffFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarGlassContentKey
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il tasto con esito nella barra dichiarata (F6B.3a), in logica pura: `button`, `primary` e `confirm` con `action` e
 * `state`; la chiave del contenuto comprende lo stato, quella del vetro no; la misura non cambia fra gli stati; il tasto
 * che lavora non risponde. E il punto unico di registrazione ([FluidToolbarKind]): un tipo nuovo entra nella risoluzione
 * e nella differenza senza che le si riscriva.
 */
class FluidActionToolbarTest {

  private val metrics = fluidToolbarMetrics()
  private val textWidth: (String) -> Float = { it.length * 10f + FluidToolbarDefaults.TextPadding.value * 2f }

  private fun model(block: FluidToolbarScope.() -> Unit): FluidToolbarModel =
    fluidToolbarModel(buildFluidToolbar(null, block), textWidth)

  private fun resolve(model: FluidToolbarModel, width: Float = 400f) = resolveFluidToolbar(model, width, metrics = metrics)

  /** Condividi ed elimina in un gruppo, il salva con esito accanto, l'invio principale a destra. */
  private fun bar(save: FluidActionState, send: FluidActionState = FluidActionState.Idle): FluidToolbarScope.() -> Unit = {
    button("share", FluidIcons.IosShare, "Condividi", onClick = {})
    button("save", FluidIcons.Save, "Salva", onClick = {}, action = FluidActionPreset.Save, state = save)
    button("delete", FluidIcons.Delete, "Elimina", onClick = {})
    primary("send", FluidIcons.Send, "Invia", onClick = {}, action = FluidActionPreset.Send, state = send)
  }

  private val states = listOf(
    FluidActionState.Idle, FluidActionState.Working(null, pending = true), FluidActionState.Working(),
    FluidActionState.Working { 0.4f }, FluidActionState.Succeeded, FluidActionState.Failed, FluidActionState.Cancelled,
  )

  @Test fun `la chiave del contenuto comprende lo stato, quella del vetro no`() {
    val items = states.map { state -> model(bar(state)).items.single { it.id == "save" } }
    // Riposo, sotto soglia e annullato hanno la stessa faccia; gli altri stati ognuno la sua.
    assertEquals(5, items.map { it.content }.distinct().size)
    assertEquals(items[0].content, items[1].content)
    assertEquals(items[0].content, items[6].content)
    assertEquals("un solo vetro per tutti gli stati", 1, items.map { it.glassContent }.distinct().size)
    assertEquals(FluidToolbarKinds.Action, items[0].kind!!.type)
    // Il tasto di sempre: il vetro e il contenuto sono la stessa chiave, come prima di F6B.3a.
    val share = model(bar(FluidActionState.Idle)).items.single { it.id == "share" }
    assertNull(share.kind)
    assertEquals(share.content, share.glassContent)
  }

  @Test fun `la misura e i gruppi non cambiano fra gli stati`() {
    val shapes = states.map { state ->
      val resolved = resolve(model(bar(state, state)))
      resolved.groups.map { Triple(it.items.map { item -> item.id }, it.leftDp, it.widthDp) } to
        resolved.groups.map(::fluidToolbarGlassContentKey)
    }
    assertEquals("posizioni e larghezze", 1, shapes.map { it.first }.distinct().size)
    assertEquals("il tondo non respira per un cambio di stato", 1, shapes.map { it.second }.distinct().size)
    val resolved = resolve(model(bar(FluidActionState.Working())))
    // Il salva con esito divide il vetro con le icone vicine, come ogni `button`.
    assertEquals(listOf("share", "save", "delete"), resolved.element("save")!!.let { e -> resolved.groups.single { it.id == e.groupId } }.items.map { it.id })
    assertEquals(metrics.buttonWidthDp, resolved.element("save")!!.widthDp, 0f)
  }

  @Test fun `un cambio di stato e uno scambio di contenuto nello stesso vetro`() {
    val idle = resolve(model(bar(FluidActionState.Idle)))
    val working = resolve(model(bar(FluidActionState.Working())))
    val done = resolve(model(bar(FluidActionState.Succeeded, FluidActionState.Failed)))
    for ((from, to) in listOf(idle to working, working to done)) {
      val diff = diffFluidToolbar(from, to)
      val save = diff.elements.single { it.to?.item?.id == "save" }
      assertEquals(FluidToolbarElementChange.Matched, save.change)
      assertEquals(FluidToolbarContentChange.BlurReplace, save.content)
      assertFalse("non si sposta", save.moved)
      assertTrue(diff.groups.all { it.change == FluidToolbarGroupChange.Matched })
      val share = diff.elements.single { it.to?.item?.id == "share" }
      assertEquals("i vicini restano", FluidToolbarContentChange.None, share.content)
    }
    // Lo stesso vetro dall'inizio alla fine, e nessun tasto che viaggia.
    val memory = FluidToolbarMemory()
    memory.update(idle, FluidToolbarMotion.Full)
    val glass = memory.glassIds.values.toList()
    memory.update(working, FluidToolbarMotion.Full)
    memory.update(done, FluidToolbarMotion.Full)
    assertEquals(glass, memory.glassIds.values.toList())
    assertTrue(memory.transfers.isEmpty())
    assertTrue(memory.ghosts.isEmpty())
    assertTrue(memory.arrivals.isEmpty())
  }

  @Test fun `lo stato sotto soglia non cambia il modello visibile`() {
    val idle = resolve(model(bar(FluidActionState.Idle)))
    val pending = resolve(model(bar(FluidActionState.Working(null, pending = true))))
    val diff = diffFluidToolbar(idle, pending)
    assertTrue(diff.elements.all { it.content == FluidToolbarContentChange.None })
  }

  @Test fun `il tasto che lavora non risponde, gli esiti si`() {
    fun accepts(state: FluidActionState): Boolean {
      val declaration = buildFluidToolbar(null, bar(state, state))
      return declaration.buttons.getValue("save").type!!.acceptsTap && declaration.buttons.getValue("send").type!!.acceptsTap
    }
    assertTrue(accepts(FluidActionState.Idle))
    assertFalse(accepts(FluidActionState.Working(null, pending = true)))
    assertFalse(accepts(FluidActionState.Working()))
    assertTrue(accepts(FluidActionState.Succeeded))
    assertTrue(accepts(FluidActionState.Failed))
    val plain = buildFluidToolbar(null, bar(FluidActionState.Idle)).buttons.getValue("share")
    assertNull("un tasto senza esito non ha tipo", plain.type)
  }

  @Test fun `conferma e principale con esito restano l'azione principale`() {
    val resolved = resolve(model {
      confirm(onClick = {}, action = FluidActionPreset.Save, state = FluidActionState.Working())
    })
    val confirm = resolved.elements.single()
    assertTrue(confirm.item.isPrimary)
    assertEquals(FluidToolbarRole.Confirm, confirm.item.role)
    assertEquals(FluidToolbarKinds.Action, confirm.item.kind!!.type)
    val send = resolve(model(bar(FluidActionState.Idle, FluidActionState.Working()))).element("send")!!
    assertTrue(send.item.isPrimary)
    assertEquals(metrics.buttonWidthDp, send.widthDp, 0f)
  }

  @Test fun `un preset diverso sullo stesso tasto e un'altra identita`() {
    val save = model(bar(FluidActionState.Idle)).items.single { it.id == "save" }
    val asCopy = model {
      button("save", FluidIcons.Save, "Salva", onClick = {}, action = FluidActionPreset.Copy)
    }.items.single()
    assertNotEquals(save.glassContent, asCopy.glassContent)
  }

  // ---- Il punto unico di registrazione --------------------------------------------------------------------------

  /** Un tipo inventato, come quelli di F6B.10–11: una larghezza sua e uno stato che cambia. */
  private data class Pill(override val state: Any?) : FluidToolbarKind {
    override val type: String get() = "prova"
    override val identity: Any get() = "pillola"
    override val widthDp: Float get() = 120f
  }

  @Test fun `un tipo nuovo entra nella risoluzione e nella differenza senza riscriverle`() {
    fun bar(state: Any?) = FluidToolbarModel(listOf(
      FluidToolbarItem("a", symbol = FluidIcons.IosShare),
      FluidToolbarItem("pill", FluidToolbarRole.Text, kind = Pill(state), widthDp = Pill(state).widthDp),
    ))
    val one = resolve(bar("in corso"))
    val two = resolve(bar("finito"))
    assertEquals(120f, one.element("pill")!!.widthDp, 0f)
    assertEquals(one.groups.map { it.leftDp to it.widthDp }, two.groups.map { it.leftDp to it.widthDp })
    val diff = diffFluidToolbar(one, two)
    val pill = diff.elements.single { it.to?.item?.id == "pill" }
    assertEquals(FluidToolbarElementChange.Matched, pill.change)
    assertEquals(FluidToolbarContentChange.BlurReplace, pill.content)
    assertEquals(fluidToolbarGlassContentKey(one.groups.last()), fluidToolbarGlassContentKey(two.groups.last()))
    // Due tipi diversi con la stessa identità non hanno mai lo stesso contenuto.
    val action = FluidToolbarItem("pill", FluidToolbarRole.Text, kind = FluidToolbarActionKind(FluidActionKind.Save,
      fluidActionFace(FluidActionPreset.Save, FluidActionState.Idle)))
    assertNotEquals(action.content, bar("in corso").items.last().content)
  }
}
