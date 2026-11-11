package dev.antigravity.fluidengine.ui.glass.backdrop.highlight

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCache
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.ShapeProvider
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'anello di ogni lastra (`HighlightNode`) e la lambda di `record` rieseguita.
 *
 * Compose riesegue da se' la lambda passata a `GraphicsLayer.record` quando la piattaforma ha buttato
 * la display list del layer (`GraphicsLayer.recreateDisplayListIfNeeded`). Lo shader del riflesso e'
 * un'istanza di processo i cui uniform li scrive ogni superficie che registra il suo anello: se il
 * pennello si configura FUORI dalla lambda, la riesecuzione disegna l'anello con la configurazione
 * dell'ultima superficie passata di li'. E' il parente del "contorno fantasma" di Fluid-physics
 * (docs/liquid-glass/ricerca/contorno-fantasma.md, sezione 9).
 *
 * Cosa si vede sul JVM. Lo shader esiste solo da API 33, e negli stub `isRuntimeShaderSupported()` e'
 * falso: gli uniform non si possono leggere. Si legge invece **quale configurazione viene letta, e
 * quando**: uno stile di prova ([TaggedStyle]) annota ogni lettura del proprio colore, che e' la
 * prima cosa che la configurazione del pennello fa, e il canvas annota ogni disegno dell'anello. Gli
 * uniform li scrive lo stesso passo, dallo stesso stile, subito dopo: che arrivino giusti allo shader
 * lo mostra il dispositivo, non questo test. Sul Tab S9 (S2-pre, C10) gli angoli dell'anello coincidono prima e dopo il
 * riciclo di una lista e il ritorno dallo sfondo con un foglio aperto (0 pixel cambiati nel foglio).
 *
 * Il banco e' quello di `PhysicsShadowSlotsTest`: `GraphicsLayer` veri di Compose sopra un doppio
 * della piattaforma. In modo software `GraphicsLayer.draw` riesegue la lambda a ogni disegno (la
 * riesecuzione tardiva del dispositivo, sempre); in modo hardware la esegue alla registrazione e,
 * dopo uno scarto simulato, una volta sola, come fa Compose su un dispositivo.
 */
class HighlightRingReplayTest {

  private val events = mutableListOf<String>()

  @Test
  fun aReplayedRingIsDrawnWithItsOwnPaintNotTheLastSurfaces() {
    val bench = RingBench(hardware = false)
    val pane = bench.ring("A", Size(300f, 120f), RoundedCornerShape(16.dp))
    val button = bench.ring("B", Size(120f, 120f), RoundedCornerShape(60.dp))

    bench.frame(pane)
    bench.frame(button)
    // Il pannello non ha niente da registrare di nuovo: la sua lambda si riesegue e basta.
    bench.frame(pane)

    val lastRing = events.lastIndexOf("anello")
    val paintBefore = events.subList(0, lastRing).lastOrNull { it.startsWith("pennello:") }
    assertEquals(
      "rieseguito, l'anello del pannello si disegna col pennello configurato da un'altra superficie " +
        "(eventi: $events)",
      "pennello:A",
      paintBefore,
    )
  }

  @Test
  fun theRingIsConfiguredAtRecordAndAtReplayNeverPerFrame() {
    val bench = RingBench(hardware = true)
    val pane = bench.ring("A", Size(300f, 120f), RoundedCornerShape(16.dp))

    repeat(10) { bench.frame(pane) }
    assertEquals("dieci fotogrammi: una registrazione, una configurazione", 1, events.count { it == "pennello:A" })
    assertEquals("dieci fotogrammi: l'anello registrato una volta", 1, events.count { it == "anello" })

    // La piattaforma butta la display list: Compose riesegue la lambda una volta, al disegno dopo.
    pane.discardDisplayList()
    repeat(5) { bench.frame(pane) }
    assertEquals("dopo uno scarto l'anello si rifa' una volta", 2, events.count { it == "anello" })
    assertEquals(
      "dopo uno scarto la riesecuzione riconfigura il pennello, una volta (eventi: $events)",
      2,
      events.count { it == "pennello:A" },
    )
  }

  /** Uno stile che annota quando la configurazione del pennello lo legge. Niente shader: sul JVM non c'e'. */
  private inner class TaggedStyle(private val tag: String) : HighlightStyle {
    override val color: Color
      get() {
        events += "pennello:$tag"
        return Color.White.copy(alpha = 0.5f)
      }

    override val blendMode: BlendMode = BlendMode.Plus

    override fun DrawScope.createShader(
      shape: Shape,
      size: Size,
      density: Density,
      runtimeShaderCache: RuntimeShaderCache,
    ): RuntimeShader? = null
  }

  /** Un anello sotto prova: il nodo, la sua taglia e il comando del suo layer. */
  private class Ring(val node: HighlightNode, val size: Size, private val control: LayerControl) {
    fun discardDisplayList() {
      control.hasDisplayList = false
    }
  }

  private class LayerControl {
    var hasDisplayList = false
  }

  /**
   * Il banco: un canvas di piattaforma che annota ogni anello disegnato, e anelli i cui layer sono
   * `GraphicsLayer` veri sopra un doppio. [hardware]: il canvas si dice accelerato, e la registrazione
   * esegue la lambda una volta come la registrerebbe un `RenderNode`.
   */
  private inner class RingBench(private val hardware: Boolean) {
    private val log = RingLog(hardware)
    private val canvas = Canvas(log)
    private val scope = CanvasDrawScope()

    fun ring(tag: String, size: Size, shape: Shape): Ring {
      // Uno stile per anello, sempre lo stesso: uno nuovo a ogni fotogramma farebbe registrare di nuovo.
      val highlight = Highlight(width = 2.dp, style = TaggedStyle(tag))
      val node = HighlightNode(ShapeProvider { shape }) { highlight }
      val control = LayerControl()
      val layer = fakeLayer(control)
      HighlightNode::class.java.getDeclaredField("highlightLayer").apply { isAccessible = true }.set(node, layer)
      return Ring(node, size, control)
    }

    fun frame(ring: Ring) {
      scope.draw(Density(1f), LayoutDirection.Ltr, canvas, ring.size) {
        val content = ContentScope(this)
        with(ring.node) { content.draw() }
      }
    }

    private fun fakeLayer(control: LayerControl): GraphicsLayer {
      givePlatformAFingerprint()
      val implClass = Class.forName("androidx.compose.ui.graphics.layer.GraphicsLayerImpl")
      val properties = HashMap<String, Any?>()
      val impl = Proxy.newProxyInstance(implClass.classLoader, arrayOf(implClass)) { proxy, method, args ->
        val name = method.name.substringBefore('-')
        when {
          name == "equals" -> proxy === args?.get(0)
          name == "hashCode" -> System.identityHashCode(proxy)
          name == "toString" -> "FakeGraphicsLayerImpl"
          name == "getHasDisplayList" -> hardware && control.hasDisplayList
          name == "getSupportsSoftwareRendering" -> false
          name == "record" && hardware -> {
            // Come un RenderNode: la lambda si esegue adesso, in una registrazione.
            val layer = args!![2] as GraphicsLayer
            @Suppress("UNCHECKED_CAST")
            val block = args[3] as (DrawScope) -> Unit
            CanvasDrawScope().draw(args[0] as Density, args[1] as LayoutDirection, canvas, layer.size.toSize()) {
              block(this)
            }
            control.hasDisplayList = true
            null
          }
          name.startsWith("set") && args?.size == 1 -> {
            properties[name.removePrefix("set")] = args[0]
            null
          }
          name.startsWith("get") && args.isNullOrEmpty() ->
            properties[name.removePrefix("get")] ?: defaultOf(method)
          name.startsWith("is") && args.isNullOrEmpty() ->
            properties[name.removePrefix("is")] ?: defaultOf(method)
          else -> defaultOf(method)
        }
      }
      return GraphicsLayer::class.java.getConstructor(implClass).newInstance(impl)
    }
  }

  /** Il canvas di piattaforma: annota l'anello (un rettangolo arrotondato) e per il resto e' uno stub. */
  private inner class RingLog(private val hardware: Boolean) : android.graphics.Canvas() {
    override fun drawRoundRect(
      left: Float,
      top: Float,
      right: Float,
      bottom: Float,
      rx: Float,
      ry: Float,
      paint: android.graphics.Paint,
    ) {
      events += "anello"
    }

    override fun isHardwareAccelerated(): Boolean = hardware
  }

  /** Il contenuto del nodo e' vuoto: il test guarda solo l'anello. */
  private class ContentScope(scope: DrawScope) : ContentDrawScope, DrawScope by scope {
    override fun drawContent() = Unit
  }

  private companion object {
    /**
     * Come in `PhysicsShadowSlotsTest`: `GraphicsLayer` nel suo inizializzatore statico legge
     * `Build.FINGERPRINT`, che negli stub e' null. Un valore che non sia "robolectric", scritto con
     * `Unsafe` (e' un `static final` di uno stub), solo nel processo dei test.
     */
    fun givePlatformAFingerprint() {
      val field = android.os.Build::class.java.getDeclaredField("FINGERPRINT")
      if (field.get(null) != null) return
      val unsafeClass = Class.forName("sun.misc.Unsafe")
      val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
      val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
      val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
        .invoke(unsafe, field) as Long
      unsafeClass.getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
        .invoke(unsafe, base, offset, "fluid-engine/jvm-unit-test")
    }

    fun defaultOf(method: Method): Any? = when (method.returnType) {
      java.lang.Boolean.TYPE -> false
      java.lang.Integer.TYPE -> 0
      java.lang.Long.TYPE -> 0L
      java.lang.Float.TYPE -> 0f
      java.lang.Double.TYPE -> 0.0
      java.lang.Short.TYPE -> 0.toShort()
      java.lang.Byte.TYPE -> 0.toByte()
      java.lang.Character.TYPE -> 0.toChar()
      else -> null
    }
  }
}
