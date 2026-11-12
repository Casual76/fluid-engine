package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassNestingScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'innesto della sonda in `GlassLiquidSurface` (F2.3, `artifacts/liquid-glass/bench/f2-2/innesto.md`): finché la
 * sonda non ha misurato niente la superficie disegna **gli stessi numeri** di prima, al bit; il lato che la misura
 * scrive (il giro) agli estremi è la superficie dell'altro lato, al bit.
 */
class GlassSurfaceAdaptiveTest {

  private fun surface(
    dark: Boolean,
    adaptive: ((GlassAdaptiveMaterial, Boolean) -> GlassAdaptiveMaterial?)? = null,
    preference: Float = FluidGlassPreference.Default,
    role: GlassRole = GlassRole.Floating,
    nesting: MutableState<Boolean> = mutableStateOf(false),
  ) = GlassLiquidSurface(
    role = role, optics = GlassDefaults.optics(role).sanitized(), shape = FluidCapsuleShape, darkSide = dark,
    screenDensity = 3f, intensity = { 1f }, opticalDepth = { 1f }, pressed = { 0f }, quality = { 1f },
    film = { Color.Unspecified }, tint = { Color.Unspecified }, touch = { null },
    adaptive = adaptive, preference = preference, nesting = nesting,
  )

  /** Il nodo di un vetro dentro un altro vetro: la risposta che `drawBackdrop` dà agli effetti. */
  private object OnGlass : BackdropEffectScopeImpl(), GlassNestingScope {
    override val standsOnGlass: Boolean = true
    override val shape: Shape get() = FluidCapsuleShape
  }

  /** Tutti i campi della superficie pronta al disegno, in profondità: due superfici uguali danno la stessa mappa. */
  private fun fields(any: Any?, prefix: String = "", out: MutableMap<String, Any?> = linkedMapOf(), depth: Int = 0): Map<String, Any?> {
    if (any == null || depth > 3) {
      out[prefix] = any
      return out
    }
    when (any) {
      is Number, is Boolean, is String, is Color -> out[prefix] = any
      is FloatArray -> out[prefix] = any.toList()
      is IntArray -> out[prefix] = any.toList()
      is Enum<*> -> out[prefix] = any.name
      else -> {
        if (!any.javaClass.name.startsWith("dev.antigravity")) {
          out[prefix] = any.toString()
          return out
        }
        var klass: Class<*>? = any.javaClass
        while (klass != null && klass != Any::class.java) {
          for (f in klass.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            f.isAccessible = true
            fields(f.get(any), "$prefix.${f.name}", out, depth + 1)
          }
          klass = klass.superclass
        }
      }
    }
    return out
  }

  private val size = Size(156f * 3f, 56f * 3f)

  private fun drawn(s: GlassLiquidSurface): Map<String, Any?> = fields(s.drawInputs(size, scale = 1f))

  @Test
  fun anUnmeasuredProbeChangesNothing() {
    for (dark in listOf(false, true)) {
      val before = drawn(surface(dark))
      val withProbe = drawn(surface(dark, adaptive = { out, _ -> out.unmeasured() }))
      val nestedHost = drawn(surface(dark, adaptive = { _, _ -> null }))
      assertEquals("lato ${if (dark) "scuro" else "chiaro"}", before, withProbe)
      assertEquals(before, nestedHost)
    }
  }

  @Test
  fun aTurnedSurfaceIsTheOtherSideToTheBit() {
    val dark = drawn(surface(dark = true))
    val turned = drawn(surface(dark = false, adaptive = { out, _ -> out.unmeasured().also { it.darkness = 1f } }))
    assertEquals(dark, turned)
    val light = drawn(surface(dark = false))
    val back = drawn(surface(dark = true, adaptive = { out, _ -> out.unmeasured().also { it.darkness = 0f } }))
    assertEquals(light, back)
    // A metà giro è un'altra cosa (la mescola), e la ricetta dell'altro lato sta in un contenitore della superficie.
    val half = surface(dark = false, adaptive = { out, _ -> out.unmeasured().also { it.darkness = 0.5f } })
    assertNotEquals(light, drawn(half))
    assertEquals(drawn(half), drawn(half))
  }

  /**
   * L'anello della galleria ferma (F2.3, rilievo 1). Una superficie ricreata sullo stesso nodo (una chiave nuova: una
   * `ContinuousCornerShape`, che non è uguale a se stessa, a ogni composizione) sa già di essere annidata, e i suoi
   * effetti non scrivono la risposta. Prima ripartiva «non annidata»: gli effetti scrivevano lo stato che `glassSurface`
   * legge in composizione (per la sonda), la scrittura ricomponeva, la ricomposizione ricreava la superficie. Un giro per
   * fotogramma, per sempre.
   */
  @Test
  fun aSurfaceRemadeOnTheSameNodeKeepsTheNodesAnswer() {
    val nesting = mutableStateOf(false)
    val first = surface(dark = false, nesting = nesting)
    assertFalse("prima della risposta del nodo una lastra non è annidata", first.nested)
    first.effects(OnGlass)
    assertTrue(first.nested)
    assertTrue("la risposta sta accanto alla superficie", nesting.value)

    val remade = surface(dark = false, nesting = nesting)
    assertTrue("la superficie ricreata parte dalla risposta del nodo", remade.nested)
    var written = 0
    val observer = Snapshot.registerGlobalWriteObserver { if (it === nesting) written++ }
    try {
      remade.effects(OnGlass)
    } finally {
      observer.dispose()
    }
    assertEquals("gli effetti della superficie ricreata non riscrivono la risposta", 0, written)
    assertTrue(remade.nested)
    // E la stessa superficie ricreata disegna come una annidata dal primo fotogramma.
    assertEquals(drawn(first), drawn(remade))
  }

  /**
   * L'altra metà dello stesso anello: chi costruisce la superficie dentro un `remember` in composizione (la goccia, la
   * pillola, l'ombra) non deve leggere la risposta «annidata», o la scrittura degli effetti lo ricomporrebbe. Col rail
   * della galleria (goccia con una `ContinuousCornerShape`) era un giro ogni due fotogrammi.
   */
  @Test
  fun buildingASurfaceDoesNotReadTheNestingAnswer() {
    val nesting = mutableStateOf(true)
    val read = ArrayList<Any>()
    val built = Snapshot.observe(readObserver = { read += it }) { surface(dark = false, nesting = nesting) }
    assertFalse("la costruzione non legge la risposta: ${read.size} letture", read.any { it === nesting })
    assertTrue("ma la usa", built.nested)
  }

  @Test
  fun theCursorReachesTheSurface() {
    val middle = drawn(surface(dark = false))
    val colored = surface(dark = false, preference = FluidGlassPreference.Colored).drawInputs(size, scale = 1f)
    assertNotEquals(middle, fields(colored))
    assertEquals(middle, drawn(surface(dark = false, preference = FluidGlassPreference.Default)))
  }
}
