package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import dev.antigravity.fluidengine.ui.fluid.fluidSliderThumbCentre
import dev.antigravity.fluidengine.ui.glass.field.DropElements
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropFaces
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropGlow
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropRise
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropStretch
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropGlow
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropLeadingStretch
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropRiseScales
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropShadowAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Le tracce del movimento dei controlli che salgono nel vetro (F6.3): interruttore e cursore premuti, trascinati,
 * lasciati e (l'interruttore) toccati, con le stesse funzioni dell'engine (`GlassDropRise`, `GlassDropStretch`,
 * `glassDropRiseScales`, `glassDropGlow` in `GlassDrop.kt`; la goccia del materiale dalla ricetta, come le scene di
 * F1B.3) e le stesse molle (`FluidMotion.DropPress`, `FluidMotion.dropValue`, `GlassDropRise.TrackTintStiffness`).
 *
 * **Il dito è quello di Apple.** I tempi di pressione e di rilascio e il percorso del dito vengono da `UISwitch` e
 * `UISlider` di iOS 26 ripresi a schermo nella sessione WWDC25 284 (a tempo vero, 29,97 fps; fotogrammi del file
 * `artifacts/liquid-glass/sessioni/f6-3/apple/wwdc2025-284_2160p_1040.mp4`): per il cursore il centro della lente
 * misurato fotogramma per fotogramma (`misura-cursore-284.txt`), per l'interruttore gli istanti di pressione, corsa e
 * rilascio. Il tocco dell'interruttore è quello della sessione 219 (R4 B5). Accento come in Apple: il verde e
 * l'azzurro di sistema.
 *
 * Si scrivono con `GLASS_F63_DIR` (di solito `tools/glassbench/traces/f6-3`) e si rendono con `glassmotion.py`;
 * il confronto con Apple si fa con `pair --apple-video … --t0-frame …` (vedi `sessioni/f6-3/rapporto.md`).
 *
 * Approssimato, dichiarato: la lente del banco piega la pista dipinta nella pagina, non la sua copia schiacciata che il
 * pomello dell'interruttore porta con sé (`FluidSwitch`, `squashedTrack`); l'onda di Fluid-physics non c'entra.
 */
class F63ControlsTraceTest {

  // ----- il simulatore della goccia: GlassDragAnimation e la salita nel vetro, a passo fisso -----------------------

  /**
   * Una molla critica di pulsazione [omega], esatta su un passo a bersaglio fermo: è la molla di Compose ripartita a
   * ogni `animateTo` con la velocità che ha (così fa `GlassDragAnimation.updateValue` a ogni evento del dito).
   */
  private class Critical(var x: Float, private val omega: Float) {
    var v = 0f
    var target = x
    fun step(dt: Float) {
      val e = x - target
      val k = exp(-omega * dt)
      val c = v + omega * e
      x = target + (e + c * dt) * k
      v = (v - omega * c * dt) * k
    }
  }

  /** La goccia di un controllo che sale nel vetro: valore, pressione, stiramento e la regola del rilascio. */
  private class Drop(value: Float, pressed: Float, private val travelDp: Float, cap: Float = GlassDropRise.StretchCap) {
    val value = Critical(value, sqrt(1000f)) // FluidMotion.dropValue: (1, 1000)
    val press = Critical(pressed, sqrt(1000f)) // FluidMotion.DropPress: (1, 1000)
    val stretch = GlassDropStretch(cap = cap).also { it.reset(value * travelDp) }
    var releasePending = false

    /** Il dito tiene la goccia: lo stiramento ha il suo segno; se no solo in avanti (`followsFinger`). */
    var finger = true

    /** Lo stiramento come lo vede la lente. */
    val shown: Float get() = if (finger) stretch.value else glassDropLeadingStretch(stretch.value, stretch.velocity)

    init {
      press.target = pressed
    }

    fun press() {
      releasePending = false
      press.target = 1f
    }

    /** `GlassDragAnimation.release`: la pressione si lascia quando il valore è arrivato e la pressione anche. */
    fun release() {
      releasePending = true
    }

    fun frame(dt: Float, substeps: Int = 8) {
      val h = dt / substeps
      repeat(substeps) {
        value.step(h)
        press.step(h)
      }
      if (releasePending && abs(value.x - value.target) < 0.025f &&
        (press.target != 1f || press.x >= 0.96f)
      ) {
        releasePending = false
        press.target = 0f
      }
      stretch.step(value.x * travelDp, dt)
    }

    val p: Float get() = press.x.coerceIn(0f, 1f)
  }

  // ----- gli elementi ------------------------------------------------------------------------------------------

  private val page = Color(0xFFF2F2F7)
  private val green = Color(0xFF34C759)
  private val blue = Color(0xFF007AFF)

  /**
   * La goccia col nodo in ([x], [y]) 40 × 24 dp, alla pressione [p] e con le scale della salita: alone, corpo (con
   * l'ombra scura che sfuma con la pressione, `shadowFade = 1`), luce.
   */
  private fun knob(x: Float, y: Float, p: Float, stretch: Float, glow: Color): List<Map<String, Any?>> {
    val scales = glassDropRiseScales(p, GlassDropRise.Grown, stretch)
    val sx = scales.xDivisor
    val sy = scales.yFactor
    val out = ArrayList<Map<String, Any?>>()
    val g = glassDropGlow(KnobW * sx, KnobH * sy, p)
    if (g.alpha > 0.002f) {
      out += linkedMapOf(
        "kind" to "shader", "name" to "alone",
        "source" to "file:tools/glassbench/traces/f6-3/alone-goccia.agsl",
        "x" to round3(x + KnobW / 2f - g.radiusX), "y" to round3(y + KnobH / 2f + g.centerDy - g.radiusY),
        "w" to round3(2f * g.radiusX), "h" to round3(2f * g.radiusY),
        "inputs" to emptyMap<String, Any?>(),
        "uniforms" to linkedMapOf(
          "center" to "\$center", "radii" to "\$halfSize",
          "color" to glassColor(glow.copy(alpha = g.alpha)), "core" to GlassDropGlow.Core,
        ),
      )
    }
    val (body, light) = DropElements.drop(
      "pomello", x = x, y = y, w = KnobW, h = KnobH, p = p, s = (sx + sy) / 2f, dark = false,
      face = GlassDropFaces.Light,
    )
    fun reshape(el: MutableMap<String, Any?>) {
      el["x"] = round3(x - (KnobW * sx - KnobW) / 2f)
      el["y"] = round3(y - (KnobH * sy - KnobH) / 2f)
      el["w"] = round3(KnobW * sx)
      el["h"] = round3(KnobH * sy)
    }
    reshape(body)
    @Suppress("UNCHECKED_CAST")
    (body["shadow"] as? List<MutableMap<String, Any?>>)?.forEach { layer ->
      layer["alpha"] = round3((layer["alpha"] as Number).toFloat() * glassDropShadowAmount(1f, p))
    }
    @Suppress("UNCHECKED_CAST")
    val faceItems = body.remove("items") as List<MutableMap<String, Any?>>
    faceItems.first().also { it["w"] = round3(KnobW * sx); it["h"] = round3(KnobH * sy) }
    body["content"] = listOf(linkedMapOf("name" to "faccia", "items" to faceItems))
    body["contentClip"] = "shape"
    light.forEach {
      reshape(it)
      DropElements.lightRadii(it, body)
    }
    completeBody(body)
    out += body
    out += light
    return out
  }

  /** Gli uniform della luce in corpo e dell'ombra in corpo, spenti, che il validatore chiede (come `DropTrace`). */
  @Suppress("UNCHECKED_CAST")
  private fun completeBody(element: Map<String, Any?>) {
    val body = (element["body"] as? Map<String, Any?>)?.get("uniforms") as? MutableMap<String, Any?> ?: return
    body.putIfAbsent("rimWidth", glassDp(0f))
    body.putIfAbsent("rimDark", "#00000000")
    body.putIfAbsent("rimFeather", 0)
    body.putIfAbsent("rimLift", 0)
    body.putIfAbsent("reflexDir", listOf(0, -1))
    body.putIfAbsent("reflexWidths", listOf(0, 0))
    body.putIfAbsent("reflexStrength", 0)
    body.putIfAbsent("reflexDepth", glassDp(0f))
    body.putIfAbsent("reflexFloor", 0)
    body.putIfAbsent("reflexHalo", 0)
    body.putIfAbsent("glowCenter", "\$center")
    body.putIfAbsent("glowRadius", glassDp(0f))
    body.putIfAbsent("glow", 0)
    body.putIfAbsent("glowFill", 0)
    body.putIfAbsent("lightColor", "#FFFFFFFF")
    body.putIfAbsent("shadowOffset", listOf(glassDp(0f), glassDp(0f)))
    body.putIfAbsent("shadowRadii", listOf(glassDp(0f), glassDp(0f)))
    body.putIfAbsent("shadowAlphas", listOf(0, 0))
    body.putIfAbsent("shadowColor", "#00000000")
    body.putIfAbsent("nodeClip", listOf(0, 0))
  }

  // ----- l'interruttore ----------------------------------------------------------------------------------------

  private val switchTrackX = 48f
  private val switchTrackY = 36f
  private val switchOff = Color(0xFF787878).copy(alpha = 0.2f)

  /** Il colore della pista e dell'alone: la molla critica `GlassDropRise.TrackTintStiffness` dietro il pomello. */
  private class Tint(start: Float) {
    private val spring = Critical(start, sqrt(GlassDropRise.TrackTintStiffness))
    fun update(position: Float, dtNanos: Long): Float {
      spring.target = position
      if (dtNanos > 0L) {
        val h = dtNanos / 1e9f / 8f
        repeat(8) { spring.step(h) }
      }
      return spring.x.coerceIn(0f, 1f)
    }
  }

  private fun switchFrame(t: Double, drop: Drop, tint: Float): F63Frame {
    val trackColor = lerp(switchOff, green, tint)
    val nodeX = switchTrackX + 2f + 20f * drop.value.x
    val nodeY = switchTrackY + 2f
    val glow = lerp(Color(0xFF787878), green, tint)
    return F63Frame(
      t,
      knob(nodeX, nodeY, drop.p, drop.shown, glow),
      listOf(
        linkedMapOf(
          "kind" to "rect", "x" to switchTrackX, "y" to switchTrackY, "w" to 64, "h" to 28, "radius" to 14,
          "color" to glassColor(trackColor.compositeOver(page)),
        ),
      ),
    )
  }

  /**
   * Un gesto sull'interruttore a 120 Hz, scritto a 60. [script] riceve l'istante in ms e la goccia, e muove il dito.
   */
  private fun switchTrace(
    name: String,
    durationMs: Double,
    startValue: Float,
    startPressed: Float,
    note: String,
    script: (Double, Drop) -> Unit,
  ): F63Trace {
    val drop = Drop(startValue, startPressed, travelDp = 20f, cap = GlassDropRise.SwitchStretchCap)
    val tint = Tint(if (startValue >= 0.5f) 1f else 0f)
    val frames = ArrayList<F63Frame>()
    val dt = 1f / 120f
    var k = -4
    while (true) {
      val t = k * 1000.0 / 120.0
      if (t > durationMs) break
      if (k >= 0) {
        script(t, drop)
        drop.frame(dt)
      }
      val tinted = tint.update(drop.value.x, if (k > 0) (dt * 1e9).toLong() else 0L)
      if (k % 2 == 0) frames += switchFrame(t, drop, tinted)
      k++
    }
    return F63Trace(
      name = name, widthDp = 160f, frames = frames,
      head = linkedMapOf(
        "_" to note,
        "palette" to "engine",
        "background" to linkedMapOf("kind" to "color", "color" to "#F2F2F7"),
        "probes" to listOf(
          linkedMapOf("name" to "larghezza", "kind" to "value", "path" to "pomello.w", "label" to "la lente (dp)", "unit" to "dp"),
          linkedMapOf("name" to "altezza", "kind" to "value", "path" to "pomello.h", "label" to "la lente (dp)", "unit" to "dp"),
        ),
      ),
    )
  }

  // ----- il cursore --------------------------------------------------------------------------------------------

  private val sliderTrackX = 40f
  private val sliderTrackLen = 224f
  private val sliderCenterY = 45f
  private val sliderGray = Color(0xFF1C1B1F).copy(alpha = 0.12f)

  /** Il centro della lente del cursore di Apple (284), in px del ritaglio (900, 900, 1920, 400); pista da 240 a 1880. */
  private val appleSlider: Map<Int, Float> by lazy {
    mapOf(
      1522 to 1057f, 1523 to 1053f, 1524 to 1018f, 1525 to 956f, 1526 to 911f, 1527 to 871f, 1528 to 797f,
      1529 to 734f, 1530 to 679f, 1531 to 588f, 1532 to 511f, 1533 to 448f, 1534 to 398f,
      1553 to 374f, 1554 to 388f, 1555 to 419f, 1556 to 513f, 1557 to 567f, 1558 to 628f, 1559 to 690f,
      1560 to 779f, 1561 to 872f, 1562 to 945f, 1563 to 1052f, 1564 to 1151f, 1565 to 1238f, 1566 to 1316f,
      1567 to 1395f, 1568 to 1458f, 1569 to 1515f, 1570 to 1558f, 1571 to 1594f,
      1608 to 1614f, 1609 to 1568f, 1610 to 1519f, 1611 to 1462f, 1612 to 1411f, 1613 to 1347f, 1614 to 1284f,
      1615 to 1221f, 1616 to 1166f, 1617 to 1082f, 1618 to 1061f, 1619 to 1060f, 1620 to 1057f, 1621 to 1054f,
      1622 to 1049f, 1623 to 1047f, 1624 to 1048f, 1625 to 1050f, 1626 to 1052f, 1627 to 1055f, 1628 to 1056f,
      1629 to 1058f, 1630 to 1060f, 1631 to 1060f, 1632 to 1061f, 1633 to 1060f, 1634 to 1061f, 1635 to 1061f,
      1636 to 1057f, 1637 to 1058f, 1638 to 1051f, 1639 to 1058f, 1640 to 1057f,
    )
  }

  /** Il dito di Apple come frazione della pista, all'istante [ms] da [n0], interpolato fra i fotogrammi. */
  private fun appleFinger(n0: Int, ms: Double): Float {
    val f = n0 + ms / AppleFrameMs
    val a = f.toInt()
    val pa = appleSlider[a] ?: appleSlider.filterKeys { it <= a }.maxByOrNull { it.key }!!.value
    val pb = appleSlider[a + 1] ?: pa
    val px = pa + (pb - pa) * (f - a).toFloat()
    return ((px - 240f) / 1640f).coerceIn(0f, 1f)
  }

  private fun sliderFrame(t: Double, drop: Drop): F63Frame {
    val progress = drop.value.x.coerceIn(0f, 1f)
    val centre = sliderTrackX + fluidSliderThumbCentre(progress, sliderTrackLen, KnobW)
    val trackY = sliderCenterY - 3f
    return F63Frame(
      t,
      knob(centre - KnobW / 2f, sliderCenterY - KnobH / 2f, drop.p, drop.shown, blue),
      listOf(
        linkedMapOf(
          "kind" to "rect", "x" to sliderTrackX, "y" to trackY, "w" to sliderTrackLen, "h" to 6, "radius" to 3,
          "color" to glassColor(sliderGray.compositeOver(page)),
        ),
        linkedMapOf(
          "kind" to "rect", "x" to sliderTrackX, "y" to trackY, "w" to round3(sliderTrackLen * progress), "h" to 6,
          "radius" to 3, "color" to glassColor(blue),
        ),
      ),
    )
  }

  private fun sliderTrace(
    name: String,
    n0: Int,
    durationMs: Double,
    pressAtMs: Double?,
    releaseAtMs: Double?,
    startPressed: Float,
    note: String,
  ): F63Trace {
    val drop = Drop(appleFinger(n0, 0.0), startPressed, travelDp = sliderTrackLen)
    val frames = ArrayList<F63Frame>()
    val dt = 1f / 120f
    var k = -4
    while (true) {
      val t = k * 1000.0 / 120.0
      if (t > durationMs) break
      if (k >= 0) {
        if (pressAtMs != null && t >= pressAtMs && t - pressAtMs < 1000.0 / 120.0) drop.press()
        if (releaseAtMs != null && t >= releaseAtMs && t - releaseAtMs < 1000.0 / 120.0) drop.release()
        // Il dito muove il valore solo mentre la tiene, come lo slider (`report` a ogni evento).
        val held = (pressAtMs == null || t >= pressAtMs) && (releaseAtMs == null || t < releaseAtMs)
        drop.finger = held
        if (held) drop.value.target = appleFinger(n0, t)
        drop.frame(dt)
      }
      if (k % 2 == 0) frames += sliderFrame(t, drop)
      k++
    }
    return F63Trace(
      name = name, widthDp = 310f, frames = frames,
      head = linkedMapOf(
        "_" to note,
        "palette" to "engine",
        "background" to linkedMapOf("kind" to "color", "color" to "#F2F2F7"),
        "probes" to listOf(
          linkedMapOf("name" to "larghezza", "kind" to "value", "path" to "pomello.w", "label" to "la lente (dp)", "unit" to "dp"),
          linkedMapOf("name" to "altezza", "kind" to "value", "path" to "pomello.h", "label" to "la lente (dp)", "unit" to "dp"),
        ),
      ),
    )
  }

  // ----- le tracce -------------------------------------------------------------------------------------------

  private fun all(): List<F63Trace> {
    val half = AppleFrameMs / 2.0
    var arrivedAt = -1.0
    return listOf(
      // 284, f696: il dito scende fra f696 e f697 sul pomello acceso e lo tiene.
      switchTrace(
        "interruttore-pressione", 420.0, startValue = 1f, startPressed = 0f,
        note = "F6.3: pressione sul pomello acceso, tenuto. Apple: 284 f696 (il dito a meta' fra f696 e f697).",
      ) { t, drop -> if (t >= half && drop.press.target == 0f) drop.press() },
      // 284, f726: la lente tenuta va da acceso a spento (f727-f735).
      switchTrace(
        "interruttore-corsa", 520.0, startValue = 1f, startPressed = 1f,
        note = "F6.3: lente tenuta che il dito porta da acceso a spento in ~110 ms (Apple la fa attraversare fra " +
          "f727 e f730). Apple: 284 f726.",
      ) { t, drop ->
        val u = ((t - half) / 110.0).coerceIn(0.0, 1.0).toFloat()
        drop.value.target = 1f - u * u * (3f - 2f * u)
      },
      // 284, f796: il dito lascia la lente sul lato acceso.
      switchTrace(
        "interruttore-rilascio", 340.0, startValue = 1f, startPressed = 1f,
        note = "F6.3: rilascio della lente ferma sul lato acceso. Apple: 284 f796 (il dito si alza fra f796 e f797).",
      ) { t, drop ->
        if (t >= half && drop.press.target == 1f && !drop.releasePending) {
          drop.finger = false
          drop.release()
        }
      },
      // 219, il tocco: lente sul posto, corsa dopo 100 ms, chiusura 60 ms dopo l'arrivo.
      switchTrace(
        "interruttore-tocco", 700.0, startValue = 1f, startPressed = 0f,
        note = "F6.3: un tocco che spegne. La lente si forma sul posto, corre dopo " +
          "${GlassDropRise.TapTravelDelayMillis} ms, resta ${GlassDropRise.TapDwellMillis} ms all'arrivo e si richiude. " +
          "Apple: 219 f7756 (R4 B5).",
      ) { t, drop ->
        // Un tocco: il dito si alza subito, e la lente corre da sola.
        drop.finger = false
        if (t >= half && drop.press.target == 0f && drop.value.target == 1f && arrivedAt < 0.0) drop.press()
        if (t >= half + GlassDropRise.TapTravelDelayMillis && drop.value.target == 1f) drop.value.target = 0f
        if (drop.value.target == 0f && arrivedAt < 0.0 && abs(drop.value.x) < 0.025f) arrivedAt = t
        if (arrivedAt >= 0.0 && t >= arrivedAt + GlassDropRise.TapDwellMillis && drop.press.target == 1f &&
          !drop.releasePending
        ) {
          drop.release()
        }
      },
      sliderTrace(
        "cursore-presa", n0 = 1522, durationMs = 400.0, pressAtMs = half, releaseAtMs = null, startPressed = 0f,
        note = "F6.3: il cursore preso e portato a sinistra dal dito di Apple (284 f1522-f1534).",
      ),
      sliderTrace(
        "cursore-lancio", n0 = 1553, durationMs = 600.0, pressAtMs = null, releaseAtMs = null, startPressed = 1f,
        note = "F6.3: la lente tenuta lanciata a destra dal dito di Apple (284 f1553-f1571).",
      ),
      sliderTrace(
        "cursore-arresto", n0 = 1608, durationMs = 1250.0, pressAtMs = null, releaseAtMs = 25 * AppleFrameMs + half,
        startPressed = 1f,
        note = "F6.3: la lente riportata a sinistra, fermata e lasciata, dal dito di Apple (284 f1608-f1640; " +
          "il dito si alza fra f1633 e f1634).",
      ),
    )
  }

  @Test
  fun `esporta pressione, corsa, rilascio e tocco dell'interruttore e tre gesti del cursore`() {
    val traces = all()
    assertEquals(7, traces.size)
    for (t in traces) {
      GlassTraceOutput.requestedDir(System.getenv("GLASS_F63_DIR"))?.let { t.write(File(it, "${t.name}/trace.json")) }
      // Il fotogramma a t = 0 c'e', i tempi crescono: lo controlla la traccia stessa.
      t.trace()
    }
    // La pressione arriva senza superamento: la lente non passa mai ×1,5 (60 dp).
    val pressure = traces.first { it.name == "interruttore-pressione" }
    val widths = pressure.frames.map { f -> f.elements.first { it["name"] == "pomello" }["w"] as Double }
    assertTrue(widths.max() <= KnobW * GlassDropRise.Grown + 1e-3)
    assertEquals(KnobW * GlassDropRise.Grown.toDouble(), widths.last(), 0.05)
    // Il tocco: la lente si forma prima di muoversi, e alla fine il pomello è bianco a riposo sul lato spento.
    val tap = traces.first { it.name == "interruttore-tocco" }
    val ws = tap.frames.map { f -> f.elements.first { it["name"] == "pomello" }["w"] as Double }
    // Il centro, non il bordo: la lente cresce attorno al centro e il bordo sinistro si sposta anche da ferma.
    val xs = tap.frames.mapIndexed { i, f -> (f.elements.first { it["name"] == "pomello" }["x"] as Double) + ws[i] / 2.0 }
    val firstMove = xs.indexOfFirst { it < xs.first() - 0.5 }
    assertTrue("si muove a ${tap.frames[firstMove].tMs} ms", tap.frames[firstMove].tMs >= GlassDropRise.TapTravelDelayMillis)
    assertTrue("larga ${ws[firstMove]} quando parte", ws[firstMove] > KnobW * 1.2)
    // Tornata pomello (entro un quinto di dp: la molla critica della pressione ha ancora qualche millesimo).
    assertEquals(KnobW.toDouble(), ws.last(), 0.2)
    // Il cursore lasciato: torna pomello, e a riposo non è stirato.
    val stop = traces.first { it.name == "cursore-arresto" }
    val last = stop.frames.last().elements.first { it["name"] == "pomello" }
    assertEquals(KnobW.toDouble(), last["w"] as Double, 0.2)
    assertEquals(KnobH.toDouble(), last["h"] as Double, 0.2)
  }

  private companion object {
    const val KnobW = 40f
    const val KnobH = 24f
    const val AppleFrameMs = 1001.0 / 30.0
  }
}

/** Un fotogramma con la pagina sua (TRACE.md: la `page` di un fotogramma sostituisce quella della traccia). */
private class F63Frame(val tMs: Double, val elements: List<Map<String, Any?>>, val page: List<Map<String, Any?>>)

/**
 * Una traccia di F6.3: quella del banco (`GlassTrace`, che controlla tempi e nome e scrive la testa) con la pagina di
 * ogni fotogramma, che `GlassTraceFrame` non porta: la pista cambia colore e il riempimento del cursore si allunga.
 */
private class F63Trace(
  val name: String,
  private val widthDp: Float,
  val frames: List<F63Frame>,
  private val head: Map<String, Any?>,
) {
  fun trace(): GlassTrace = GlassTrace(
    name = name, phase = "f6-3", density = 3f, theme = "light", widthDp = widthDp, heightDp = 100f,
    frames = frames.map { GlassTraceFrame(it.tMs, it.elements) }, fps = 60, head = head,
  )

  fun write(file: File) {
    val json = LinkedHashMap(trace().toJson())
    json["frames"] = frames.map { linkedMapOf("t" to it.tMs, "elements" to it.elements, "page" to it.page) }
    file.parentFile?.mkdirs()
    file.writeText(GlassTraceJson.encode(json), Charsets.UTF_8)
  }
}
