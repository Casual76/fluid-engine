package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLiquidSurface
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quando `DrawBackdropNode` rifà gli effetti (giro di correzione unico di F1B; voce 20 di `LICENSES/AndroidLiquidGlass.md`;
 * rilievi R1 del critico C e 7 del critico D).
 *
 * Il nodo fa girare gli effetti all'attacco (la taglia non c'è ancora), quando cambia uno stato letto in un giro
 * osservato, e nel disegno quando lo scope prende la taglia. A monte quest'ultimo giro non era osservato: gli ingressi
 * che un effetto legge solo **dopo** la taglia (il corpo del vetro nuovo esce senza leggere niente finché la taglia
 * manca; la superficie fisica, a intensità zero, esce prima del resto) non rifacevano mai la catena. Qui una superficie
 * **stabile** (stesso elemento, stessa taglia, stesso fondale) cambia **un ingresso alla volta**, e ogni cambio deve
 * rifare la catena.
 *
 * Il nodo è un modello di tre righe, ma la regola è quella vera: il modello usa [BackdropEffectsUpdates] così com'è,
 * con un [SnapshotStateObserver] di Compose al posto di `observeReads` e le superfici vere ([GlassLiquidSurface],
 * [PhysicsLiquidSurface]). Togliere la correzione (in `onDraw`, `rebuild()` invece di `refresh()`) fa fallire i primi
 * tre test; l'ultimo prova che la regola di monte, scritta qui accanto, li perde.
 */
class BackdropEffectsUpdatesTest {

  private val observer = SnapshotStateObserver { it() }.also { it.start() }
  private val stale = LinkedHashSet<Node>()

  @After
  fun stop() {
    observer.stop()
    observer.clear()
  }

  /** Il nodo: lo scope, la regola, e quante volte la catena si è rifatta. */
  private inner class Node(private val effects: BackdropEffectScope.() -> Unit, private val upstream: Boolean = false) {
    private val scope = object : BackdropEffectScopeImpl() {
      override val shape: Shape = RectangleShape
    }
    var rebuilds = 0
      private set
    private val rebuild: () -> Unit = {
      scope.apply(effects)
      rebuilds++
    }
    private val onChanged: (Node) -> Unit = { stale += it }
    val updates = BackdropEffectsUpdates(observe = { block -> observer.observeReads(this, onChanged, block) }, rebuild = rebuild)

    /** `onAttach` → `observeEffects`. */
    fun attach() = updates.refresh()

    /** `draw`: lo scope prende la taglia (cambiata solo al primo disegno). */
    fun draw(size: Size) {
      val changed = scope.size != size
      if (changed) scope.size = size
      if (upstream) {
        if (changed) rebuild() // a monte: fuori dall'osservazione
      } else {
        updates.onDraw(changed)
      }
    }

    /** `onObservedReadsChanged` → `invalidateDrawCache` → `observeEffects`. */
    fun onObservedReadsChanged() = updates.refresh()
  }

  /** Compose applica: gli osservatori di ciò che è cambiato rifanno i loro giri. */
  private fun applyChanges() {
    Snapshot.sendApplyNotifications()
    val nodes = stale.toList()
    stale.clear()
    nodes.forEach { it.onObservedReadsChanged() }
  }

  /** Attacco, primo disegno (la taglia arriva), qualche disegno a taglia ferma. */
  private fun Node.settle() {
    // Gli stati del test nascono nello snapshot globale: senza questo, una scrittura nello stesso snapshot in cui sono nati
    // non manda notifiche. Nel prodotto nascono in composizione, e lo snapshot della composizione è applicato.
    Snapshot.notifyObjectsInitialized()
    attach()
    applyChanges()
    repeat(3) { draw(Size(600f, 300f)) }
    applyChanges()
  }

  private fun assertRebuilds(node: Node, what: String, change: () -> Unit) {
    val before = node.rebuilds
    change()
    applyChanges()
    node.draw(Size(600f, 300f))
    assertTrue("$what: cambiato a taglia, elemento e fondale fermi, la catena non si è rifatta", node.rebuilds > before)
  }

  // ---- Il vetro nuovo fermo (`glassSurface`, la goccia, la pillola: tutti passano da GlassLiquidSurface) ------------

  private class SlabInputs {
    val intensity = mutableFloatStateOf(1f)
    val depth = mutableFloatStateOf(1f)
    val pressed = mutableFloatStateOf(0f)
    val quality = mutableFloatStateOf(1f)
    val film = mutableStateOf(Color.Unspecified)
    val tint = mutableStateOf(Color.Unspecified)
  }

  private fun slab(inputs: SlabInputs) = GlassLiquidSurface(
    role = GlassRole.Floating,
    optics = GlassDefaults.buttonOptics().sanitized(),
    shape = FluidCapsuleShape,
    darkSide = true,
    screenDensity = 2f,
    intensity = { inputs.intensity.floatValue },
    opticalDepth = { inputs.depth.floatValue },
    pressed = { inputs.pressed.floatValue },
    quality = { inputs.quality.floatValue },
    film = { inputs.film.value },
    tint = { inputs.tint.value },
    touch = { null },
    profile = GlassLiquidProfile.Default,
  )

  @Test
  fun aStillSlabRebuildsItsBodyForEveryInputItReadsAfterTheSize() {
    val inputs = SlabInputs()
    val node = Node(slab(inputs).effects).apply { settle() }
    assertRebuilds(node, "profondità ottica (la goccia)") { inputs.depth.floatValue = 0.4f }
    assertRebuilds(node, "pressione (la pillola)") { inputs.pressed.floatValue = 1f }
    assertRebuilds(node, "qualità (uno scorrimento)") { inputs.quality.floatValue = 0.25f }
    assertRebuilds(node, "pellicola") { inputs.film.value = Color(0x40FFFFFF) }
    assertRebuilds(node, "tinta") { inputs.tint.value = Color(0x4000C853) }
    assertRebuilds(node, "intensità (la comparsa)") { inputs.intensity.floatValue = 0.5f }
  }

  // ---- La superficie fisica (Fluid-physics col materiale nuovo) ------------------------------------------------------

  private class PhysicsInputs {
    val intensity = mutableFloatStateOf(1f)
    val quality = mutableFloatStateOf(1f)
    val tintBlend = mutableFloatStateOf(1f)
    val opticsBlend = mutableFloatStateOf(1f)
  }

  private fun physical(inputs: PhysicsInputs) = PhysicsLiquidSurface(
    state = FluidPhysicsState(FluidForm.Slab(Rect(40f, 40f, 440f, 240f))).apply { density = 2f },
    role = GlassRole.Modal,
    material = PhysicsMaterialSource(GlassDefaults.buttonOptics().sanitized(), GlassDefaults.panelOptics(compact = true).sanitized()),
    darkSide = true,
    screenDensity = 2f,
    tint = NeutralTint,
    tintFrom = NeutralTint,
    pressState = null,
    glowWeight = 1f,
    profile = GlassLiquidProfile.Default,
    intensity = { inputs.intensity.floatValue },
    tintBlend = { inputs.tintBlend.floatValue },
    opticsBlend = { inputs.opticsBlend.floatValue },
    quality = { inputs.quality.floatValue },
    layoutWidth = { 600f },
    onPanel = { false },
  )

  @Test
  fun aStillPhysicalSurfaceRebuildsItsBodyForEveryInputItReadsAfterTheSize() {
    val inputs = PhysicsInputs()
    val node = Node(physical(inputs).effects).apply { settle() }
    assertRebuilds(node, "qualità") { inputs.quality.floatValue = 0.25f }
    assertRebuilds(node, "miscela dell'ottica (un menu che arriva)") { inputs.opticsBlend.floatValue = 0.5f }
    assertRebuilds(node, "miscela della tinta") { inputs.tintBlend.floatValue = 0.5f }
    assertRebuilds(node, "intensità") { inputs.intensity.floatValue = 0.5f }
  }

  /** Una superficie che nasce spenta: il giro che la accende apre un ramo, e ciò che il ramo legge va osservato. */
  @Test
  fun aSurfaceBornOffObservesTheInputsOfTheBranchItsLightingOpens() {
    val inputs = PhysicsInputs().apply { intensity.floatValue = 0f }
    val node = Node(physical(inputs).effects).apply { settle() }
    assertRebuilds(node, "accensione") { inputs.intensity.floatValue = 1f }
    assertRebuilds(node, "qualità, letta solo a superficie accesa") { inputs.quality.floatValue = 0.25f }
    assertRebuilds(node, "miscela dell'ottica, letta solo a superficie accesa") { inputs.opticsBlend.floatValue = 0.5f }
  }

  /** La regola di monte (il giro del disegno fuori dall'osservazione) perde gli ingressi letti dopo la taglia. */
  @Test
  fun theUpstreamRuleLosesTheInputsReadAfterTheSize() {
    val inputs = SlabInputs()
    val node = Node(slab(inputs).effects, upstream = true).apply { settle() }
    val before = node.rebuilds
    inputs.quality.floatValue = 0.25f
    inputs.depth.floatValue = 0.4f
    applyChanges()
    node.draw(Size(600f, 300f))
    assertEquals("a monte la catena resta quella di prima", before, node.rebuilds)
  }

  private companion object {
    val NeutralTint = GlassTint(
      overlay = Color.Transparent,
      fallback = Color.Transparent,
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    )
  }
}
