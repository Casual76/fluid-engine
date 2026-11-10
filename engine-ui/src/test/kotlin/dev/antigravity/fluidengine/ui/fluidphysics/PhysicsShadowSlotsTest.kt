package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il contorno fantasma (docs/liquid-glass/ricerca/contorno-fantasma.md): dopo Cerchio → Quadrato →
 * Cerchio il posto dell'ombra del Cerchio disegnava l'ombra del Quadrato.
 *
 * La lambda passata a `GraphicsLayer.record` non si esegue una volta sola. Compose la riesegue da sé
 * quando la piattaforma ha buttato la display list del layer (un layer rimasto un fotogramma senza
 * disegno, un'app tornata dallo sfondo: `GraphicsLayer.recreateDisplayListIfNeeded`). Se la lambda
 * legge un `Path` o un `Paint` che un'altra registrazione ha riscritto nel frattempo, disegna la
 * forma di un altro. Il contratto: **la lambda di un posto, rieseguita in qualunque momento,
 * ridisegna la forma di quel posto.**
 *
 * Come lo si prova sul JVM, senza dispositivo: due `GraphicsLayer` veri di Compose costruiti su un
 * doppio della piattaforma ([fakeLayer]), disegnati su un canvas non accelerato. Su un canvas
 * software `GraphicsLayer.draw` non ha una display list da rigiocare e **riesegue la lambda
 * registrata a ogni disegno**: e' proprio la riesecuzione tardiva che il dispositivo fa dopo uno
 * scarto, solo che qui avviene sempre. Il canvas ([PathLog]) annota quale `Path` ogni disegno passa
 * davvero.
 *
 * Il contenuto di un `Path` non si legge sul JVM (i metodi di android.graphics sono stub), quindi
 * si prova la proprieta' sulla struttura: quale oggetto `Path` disegna ogni posto. Due posti che
 * disegnano lo stesso oggetto disegnano per forza la stessa forma, cioe' l'ultima registrata.
 */
class PhysicsShadowSlotsTest {

  private val circle = FluidForm.circle(Offset(300f, 300f), 150f)
  private val square = FluidForm.Slab(Rect(170f, 170f, 430f, 430f), FluidCornerRadii.all(40f))

  @Test
  fun backOnTheCircleTheShadowRedrawsTheCircleNotTheSquare() {
    val bench = ShadowBench(FluidPhysicsState(circle))

    val firstCircle = bench.frame()
    bench.drive(from = circle, to = square, progress = 0.5f)
    bench.drive(from = circle, to = square, progress = 1f)
    val onSquare = bench.frame()
    bench.drive(from = square, to = circle, progress = 0.5f)
    bench.drive(from = square, to = circle, progress = 1f)
    val backOnCircle = bench.frame()

    // Niente prova a vuoto: a riposo l'ombra disegna davvero una sagoma (ombra e scavo).
    assertFalse("a riposo sul Cerchio l'ombra non ha disegnato niente", firstCircle.isEmpty())
    assertFalse("a riposo sul Quadrato l'ombra non ha disegnato niente", onSquare.isEmpty())
    assertFalse("tornata sul Cerchio l'ombra non ha disegnato niente", backOnCircle.isEmpty())

    assertTrue(
      "tornata sul Cerchio, l'ombra ridisegna il Path che ha registrato il Quadrato: e' il contorno " +
        "fantasma (lambda di record che legge una sagoma condivisa fra i posti)",
      backOnCircle.none { drawn -> onSquare.any { it === drawn } },
    )
    assertTrue(
      "tornata sul Cerchio, il posto del Cerchio deve ridisegnare la sagoma che aveva al primo riposo",
      backOnCircle.all { drawn -> firstCircle.any { it === drawn } },
    )
  }

  @Test
  fun inFlightTheTwoSlotsDrawTwoSilhouettes() {
    val bench = ShadowBench(FluidPhysicsState(circle))

    bench.frame()
    bench.drive(from = circle, to = square, progress = 1f)
    bench.frame()
    // Ritorno a meta': la partenza (Quadrato) e l'arrivo (Cerchio) si disegnano tutti e due, sfumati.
    val midway = bench.drive(from = square, to = circle, progress = 0.5f)

    val distinct = midway.distinctBy { System.identityHashCode(it) }
    assertTrue(
      "a meta' del ritorno partenza e arrivo disegnano lo stesso Path: un posto mostra la forma " +
        "dell'altro (disegnati ${midway.size}, sagome distinte ${distinct.size})",
      distinct.size == 2,
    )
  }

  /**
   * Un banco per [PhysicsShadowLayers]: due posti veri, un canvas che annota, uno stato fisico che
   * si guida da fuori (cosi' riposo e viaggio sono un numero, senza orologio).
   */
  private class ShadowBench(private val state: FluidPhysicsState) {
    private val log = PathLog()
    private val canvas = Canvas(log)
    private val scope = CanvasDrawScope()
    private val shadows = PhysicsShadowLayers(fakeLayer(), fakeLayer())

    /**
     * Il progresso della guida, letto dalla lambda. `driveExternally` con gli stessi estremi tiene
     * la guida che c'e' (e la sua lambda): il numero deve stare qui, non catturato nella lambda.
     */
    private var progressNow = 0f

    /** Un fotogramma dell'ombra: i `Path` (di piattaforma) che ha disegnato, in ordine. */
    fun frame(): List<android.graphics.Path> {
      log.drawn.clear()
      scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(600f, 600f)) {
        shadows.draw(this, state, radiusPx = 20f, color = Color.Black.copy(alpha = 0.16f), alpha = 0.7f)
      }
      return log.drawn.toList()
    }

    /** Il viaggio [from] → [to] guidato da fuori, fermo a [progress] (1: riposo su [to]), e il suo fotogramma. */
    fun drive(from: FluidForm.Slab, to: FluidForm.Slab, progress: Float): List<android.graphics.Path> {
      progressNow = progress
      state.driveExternally(from = from, to = to, progress = { progressNow })
      return frame()
    }
  }

  /** Il canvas di piattaforma: annota ogni `drawPath` e per il resto non fa niente (e' uno stub). */
  private class PathLog : android.graphics.Canvas() {
    val drawn = mutableListOf<android.graphics.Path>()

    override fun drawPath(path: android.graphics.Path, paint: android.graphics.Paint) {
      drawn += path
    }

    override fun isHardwareAccelerated(): Boolean = false
  }

  private companion object {
    /**
     * Un `GraphicsLayer` di Compose sopra un doppio della piattaforma (`GraphicsLayerImpl`, interno a
     * ui-graphics, costruito per riflessione). Il doppio dice di non avere mai una display list e di
     * non saper rendere in software: `GraphicsLayer.draw` su un canvas non accelerato riesegue allora
     * la lambda registrata, che e' quello che il test vuole guardare. Le proprieta' (alfa, strategia,
     * clip) le ricorda e le restituisce.
     *
     * Se un aggiornamento di Compose rinomina queste interne, il test si rompe qui, ad alta voce:
     * va riscritto il doppio, non tolto il test.
     */
    fun fakeLayer(): GraphicsLayer {
      givePlatformAFingerprint()
      val implClass = Class.forName("androidx.compose.ui.graphics.layer.GraphicsLayerImpl")
      val properties = HashMap<String, Any?>()
      val impl = Proxy.newProxyInstance(implClass.classLoader, arrayOf(implClass)) { proxy, method, args ->
        val name = method.name.substringBefore('-')
        when {
          name == "equals" -> proxy === args?.get(0)
          name == "hashCode" -> System.identityHashCode(proxy)
          name == "toString" -> "FakeGraphicsLayerImpl"
          name == "getHasDisplayList" -> false
          name == "getSupportsSoftwareRendering" -> false
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

    /**
     * `GraphicsLayer` nel suo inizializzatore statico fa `Build.FINGERPRINT.lowercase()` (per
     * riconoscere Robolectric), e negli stub del JVM quel campo e' null: senza un valore la classe
     * non si carica. E' un `static final` di uno stub, quindi lo si scrive con `Unsafe` (JDK 17,
     * nessun `--add-opens` richiesto); solo qui, nel processo dei test. Un valore che non sia
     * "robolectric": il layer deve comportarsi come su un dispositivo.
     */
    private fun givePlatformAFingerprint() {
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

    private fun defaultOf(method: Method): Any? = when (method.returnType) {
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
