package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.GlassContentTiming
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import kotlin.math.max

/**
 * Le tracce del contenuto che si sfoca e si incrocia (F3.4). Ogni grandezza viene da [GlassContentJourney], la stessa
 * funzione che usa `FluidGlassContentTransition`: qui si porta solo in dp e nel vocabolario del banco.
 *
 * Il vetro è una lastra di oggi (`glass`) e, salvo in `resta`, ferma: la forma del vetro che cambia è del piano (F3.1) e
 * del campo (F3.3). Queste tracce guardano il contenuto.
 */
internal object GlassContentTraces {
  const val Density = 3f
  const val Fps = 60
  private const val IconDp = 26f
  private const val Ink = "#1C1C1E"

  /** Un posto del vetro: dove sta il centro dell'icona, da quale chiave a quale, e (se c'è) dove va a metà. */
  internal data class Slot(
    val name: String,
    val cxFrom: Float,
    val cxTo: Float,
    val cy: Float,
    val from: String?,
    val to: String?,
    val interruptAtMs: Double? = null,
    val next: String? = null,
  ) {
    fun journey(timing: GlassContentTiming): (Double) -> GlassContentJourney {
      val start = GlassContentJourney.start(from, to, timing)
      val after = interruptAtMs?.let { start.retarget(it, next) }
      return { t -> if (after == null || t < interruptAtMs!!) start else after }
    }
  }

  /** Un vetro fermo o che cambia larghezza (in `resta`, con una `smoothstep` di servizio: non è la molla del piano). */
  internal data class GlassBox(val x: Float, val y: Float, val w: Float, val h: Float, val wTo: Float = w, val xTo: Float = x)

  internal data class Scenario(
    val name: String,
    val note: String,
    val widthDp: Float,
    val heightDp: Float,
    val glass: GlassBox,
    val slots: List<Slot>,
    val timing: GlassContentTiming = GlassContentTiming(),
    val frames: Int = 42,
    val apple: Map<String, Any?>? = null,
  ) {
    val interruptAtMs: Double? get() = slots.firstNotNullOfOrNull { it.interruptAtMs }
  }

  fun scenarios(): List<Scenario> {
    val round = GlassBox(18f, 8f, 44f, 44f)
    return listOf(
      Scenario(
        "scambio", "un'icona ne diventa un'altra: cartella → graffetta (R7 A1)", 80f, 60f, round,
        listOf(Slot("tasto", 40f, 40f, 30f, "folder", "attach_file")),
      ),
      Scenario(
        "resta", "il «più» resta e corre col vetro (R7 A3, D2): nitido; la cartella accanto diventa graffetta",
        170f, 60f, GlassBox(16f, 8f, 88f, 44f, wTo = 136f),
        listOf(
          Slot("primo", 38f, 38f, 30f, "folder", "attach_file"),
          Slot("piu", 82f, 130f, 30f, "add", "add"),
        ),
      ),
      Scenario(
        "nasce", "un'icona senza predecessore: dal vuoto, da scala 0,75 (R7 A6)", 80f, 60f, round,
        listOf(Slot("tasto", 40f, 40f, 30f, null, "add")),
      ),
      Scenario(
        "scambio-interrotto", "cartella → graffetta, a 150 ms lo stato diventa matita: riparte da dove si trova",
        80f, 60f, round,
        listOf(Slot("tasto", 40f, 40f, 30f, "folder", "attach_file", interruptAtMs = 150.0, next = "edit")),
        frames = 48,
      ),
      appleCapsule(),
    )
  }

  /**
   * La clip di 16:02 di WWDC25 219 (R7 A.3, R4 B2 c): cartella → graffetta, cestino → «A», e il «più» che nasce. La
   * tela è il ritaglio `[330, 420, 600, 245]` del video HD (4,43 px per punto: il vetro alto 195 px è un tondo da 44),
   * e T è quella della clip, 434 ms, non i 400 di serie: così i fotogrammi si allineano su `t0Frame` = f28857.
   */
  private fun appleCapsule(): Scenario {
    val k = 4.43f
    fun dp(px: Float) = px / k
    val cy = dp(123f)
    return Scenario(
      "come-apple", "la capsula di R7 A1 alla T della clip (434 ms)", dp(600f), dp(245f),
      GlassBox(dp(22f), dp(25f), dp(520f), dp(195f)),
      listOf(
        Slot("sinistra", dp(110f), dp(125f), cy, "folder", "attach_file"),
        Slot("centro", dp(287f), dp(287f), cy, "delete", "edit"),
        Slot("destra", dp(450f), dp(450f), cy, null, "add"),
      ),
      timing = GlassContentTiming(durationMs = 434f),
      frames = 36,
      apple = linkedMapOf(
        "video" to "wwdc2025-219_hd", "t0Frame" to 28857, "crop" to listOf(330, 420, 600, 245),
        "collage" to "artifacts/liquid-glass/ricerca/r7/A1_scambio_icone_gruppo_sinistra_f28856-28888.jpg",
        "_note" to "R7 A.3 e A4; R4 B2 (c). Il render di Apple zooma un poco durante la clip: vale la forma, non il pixel.",
      ),
    )
  }

  private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

  fun build(scenario: Scenario): GlassTrace {
    val journeys = scenario.slots.map { it.journey(scenario.timing) }
    val referencePx = GlassContentDefaults.BlurReference.value * Density
    val frames = (-5..scenario.frames).map { index ->
      val time = glassTraceTimeMs(index, Fps)
      val t = max(time, 0.0)
      // Il vetro di `resta` cambia larghezza nel tempo del cambio: serve solo a far correre il «più».
      val move = smoothstep((t / scenario.timing.durationMs).toFloat())
      val g = scenario.glass
      val gx = lerp(g.x, g.xTo, move)
      val gw = lerp(g.w, g.wTo, move)
      val layers = scenario.slots.flatMapIndexed { slotIndex, slot ->
        val frame = journeys[slotIndex](t).frame(t)
        val cx = lerp(slot.cxFrom, slot.cxTo, move)
        frame.layers.filter { it.key != null }.map { layer ->
          val sigmaPx = glassContentSigmaPx(layer.look.blur, referencePx)
          // Il banco legge `blurDp` come il raggio di `BlurEffect` (σ = 0,57735 r + 0,5): lo stesso conto del composable.
          val radiusDp = glassContentBlurRadiusPx(sigmaPx) / Density
          // Chi esce resta dov'era, chi entra sta dove starà (R7 A5); chi ha la stessa chiave segue il suo vetro.
          val at = when {
            slot.from == slot.to -> cx
            layer.key == slot.from -> slot.cxFrom
            else -> slot.cxTo
          }
          linkedMapOf<String, Any?>(
            "name" to "${slot.name}-${layer.key}-${layer.id}",
            "x" to (at - IconDp / 2f - gx).toDouble(), "y" to (slot.cy - IconDp / 2f - g.y).toDouble(),
            "w" to IconDp.toDouble(), "h" to IconDp.toDouble(),
            "icon" to "fluid:${layer.key}", "iconSizeDp" to IconDp.toDouble(), "color" to Ink,
            "blurDp" to radiusDp.toDouble(), "alpha" to layer.look.alpha.toDouble(),
            "scale" to layer.look.scale.toDouble(),
            "_" to "${layer.role} σ/h=${"%.4f".format(java.util.Locale.ROOT, layer.look.blur)}",
          )
        }
      }
      // Una lastra di oggi (`glass`) e non una `shape`: il banco disegna `content` solo sugli elementi con un
      // materiale (`render_element` per `shape` chiama soltanto `draw_shape`, e lo strato sparirebbe in silenzio).
      val element = linkedMapOf<String, Any?>(
        "kind" to "glass", "name" to "vetro", "role" to "Interactive", "corner" to "continuous",
        "x" to gx.toDouble(), "y" to g.y.toDouble(), "w" to gw.toDouble(), "h" to g.h.toDouble(),
        "radius" to (g.h / 2f).toDouble(), "contentClip" to "shape", "content" to layers,
      )
      GlassTraceFrame(time, listOf(element))
    }
    val head = linkedMapOf<String, Any?>(
      "_" to "F3.4: ${scenario.note}. Funzione pura di produzione (GlassContentJourney), T = " +
        "${scenario.timing.durationMs.toInt()} ms. Vetro di servizio: guarda il contenuto.",
      "background" to linkedMapOf("kind" to "color", "color" to "#C5C7CD"),
    )
    scenario.interruptAtMs?.let { at ->
      head["events"] = listOf(linkedMapOf("t" to at, "kind" to "interrupt", "label" to "lo stato cambia a metà"))
    }
    scenario.apple?.let { head["apple"] = it }
    return GlassTrace(
      name = scenario.name, phase = "f3-contenuto", density = Density, theme = "light",
      widthDp = scenario.widthDp, heightDp = scenario.heightDp, frames = frames, fps = Fps, head = head,
    )
  }

  private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }
}
