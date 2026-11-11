package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressSprings
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.fluidPressedBounds
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe

/*
 * `liquid-tasto`: lo stesso tocco di `oggi-tasto` (F0.5: tasto tondo da 44 dp premuto, trascinato di 16 dp in basso a
 * sinistra in 300 ms, rilasciato al fotogramma 34, densita' 3, 60 Hz), reso col **materiale nuovo**: corpo, luce e
 * ombra della ricetta di serie (`glassRecipe` con `pressed`), e il bagliore del dito, che nel materiale nuovo passa
 * dalla luce (`glow`, `glowCenter`) e quindi si rende. E' la verifica del moto che il piano chiedeva a F1A
 * («`oggi-tasto` reso col materiale nuovo, pressione e bagliore, `first` per i primi cinque fotogrammi»), fatta nel
 * giro di correzione.
 *
 * **Dall'engine**: le molle, la trasformazione del tasto (`fluidPressTransform`, `fluidPressedBounds`), la ricetta per
 * ruolo, taglia e pressione, il picco del bagliore (`glowPeak`), il suo centro (dove il dito e' sceso piu' la strada
 * fatta, tenuto dentro la lastra: `GlassDrawInputs.setLight`).
 *
 * **Approssimato, dichiarato**: nell'engine la lastra si disegna alla taglia di riposo e il `graphicsLayer` della
 * pressione la scala tutta (filo compreso); qui l'elemento e' grande quanto il tasto premuto e la ricetta e' quella
 * della taglia di riposo, quindi il filo e le lunghezze della luce non crescono col tasto (al piu' il 15%). Niente
 * icona: le coordinate del contenuto di un elemento `material` che si muove non sono il punto di questa prova.
 */
internal object LiquidTasto {
  const val Name = "liquid-tasto"

  fun build(): TapBuilt {
    val scene = OggiTasto.scene()
    val d = scene.density
    val fps = scene.fps
    val rest = scene.lensDp.scaled(d)
    val feedback = FluidPressFeedback.ExpandRound
    val optics = GlassDefaults.buttonOptics().sanitized()

    fun fingerAt(tMs: Double): Offset {
      val f = ((tMs - scene.dragStartMs) / (scene.dragEndMs - scene.dragStartMs)).coerceIn(0.0, 1.0).toFloat()
      return Offset(scene.dragDp.x * d * f, scene.dragDp.y * d * f)
    }

    val releaseFrame = scene.releaseFrame
    val pressIn = TodaySpring(FluidPressSprings.ExpandIn, 0f, 1f)
    val beforeRelease = glassTraceTimeNanos(releaseFrame - 1, fps)
    val pressOut = TodaySpring(FluidPressSprings.ExpandOut, pressIn.value(beforeRelease), 0f, pressIn.velocity(beforeRelease))
    val leanBack = TodayOffsetSpring(FluidPressSprings.LeanReturn, fingerAt(glassTraceTimeMs(releaseFrame - 1, fps)), Offset.Zero)

    val frames = ArrayList<GlassTraceFrame>()
    val widths = ArrayList<Float>()
    var tail = 0
    for (k in -scene.preambleFrames until scene.maxFrames) {
      val t = glassTraceTimeMs(k, fps)
      val released = k >= releaseFrame
      val sinceRelease = glassTraceTimeNanos(k - releaseFrame, fps)
      val progress = when {
        k < 0 -> 0f
        released -> pressOut.value(sinceRelease)
        else -> pressIn.value(glassTraceTimeNanos(k, fps))
      }
      val lean = when {
        k < 0 -> Offset.Zero
        released -> leanBack.value(sinceRelease)
        else -> fingerAt(t)
      }
      val transform = fluidPressTransform(
        feedback = feedback,
        progress = progress,
        lean = lean,
        width = rest.width,
        height = rest.height,
        dp = d,
        reducedMotion = false,
      )
      val shown = fluidPressedBounds(rest, transform)
      val g = progress.coerceIn(0f, 1f)
      val recipe = glassRecipe(
        role = GlassRole.Interactive,
        widthDp = rest.width / d,
        heightDp = rest.height / d,
        darkSide = false,
        optics = optics,
        pressed = g,
      )
      val wDp = shown.width / d
      val hDp = shown.height / d
      val shape = linkedMapOf<String, Any?>(
        "name" to "tasto",
        "x" to round3(shown.left / d),
        "y" to round3(shown.top / d),
        "w" to round3(wDp),
        "h" to round3(hDp),
        "radius" to "capsule",
      )
      val element = liquidElement(shape, recipe, theme = null, note = "Interactive 44 dp, pressed ${round3(g)}")
      // Il bagliore del dito, nella luce: dove il dito e' sceso (il centro) piu' la strada fatta, scalato col tasto e
      // tenuto dentro la lastra; forte quanto il picco della ricetta per la pressione.
      val scale = (transform.scaleX + transform.scaleY) / 2f
      val cx = (wDp / 2f + lean.x * scale / d).coerceIn(0f, wDp)
      val cy = (hDp / 2f + lean.y * scale / d).coerceIn(0f, hDp)
      @Suppress("UNCHECKED_CAST")
      (element["light"] as? List<MutableMap<String, Any?>>)?.forEach { layer ->
        val u = layer.getValue("uniforms") as MutableMap<String, Any?>
        u["glow"] = recipe.glowPeak * g
        u["glowCenter"] = listOf(glassDp(cx), glassDp(cy))
      }
      completeBody(element)
      frames += GlassTraceFrame(t, listOf(element))
      widths += wDp
      val settled = released && pressOut.finished(sinceRelease) && leanBack.finished(sinceRelease)
      if (settled) tail++
      if (tail > scene.tailFrames) break
    }
    val trace = GlassTrace(
      name = Name,
      phase = "f1a",
      density = d,
      theme = "light",
      widthDp = scene.canvasWidthDp,
      heightDp = scene.canvasHeightDp,
      frames = frames,
      fps = fps,
      head = linkedMapOf(
        "_" to "Il tocco di oggi-tasto col materiale NUOVO (giro di correzione di F1A): corpo, luce, ombra e bagliore " +
          "dalla ricetta di serie con pressed; lastra alla taglia premuta con la ricetta della taglia di riposo " +
          "(LiquidTasto.kt). Senza icona.",
        "palette" to "engine",
        "background" to linkedMapOf("kind" to "gradient4", "tl" to "#99989B", "tr" to "#6A6E74", "bl" to "#DDDCDF", "br" to "#595A5F"),
        "events" to listOf(
          linkedMapOf("t" to round3(glassTraceTimeMs(releaseFrame).toFloat()), "kind" to "mark", "label" to "rilascio"),
        ),
        "probes" to listOf(
          linkedMapOf("name" to "larghezza", "kind" to "value", "path" to "tasto.w", "label" to "il vetro, dai numeri (dp)", "unit" to "dp"),
          linkedMapOf("name" to "luce", "kind" to "luma", "rect" to listOf(45, 38, 20, 20), "label" to "il centro del tasto"),
        ),
      ),
    )
    return TapBuilt(trace, widths)
  }

  /** Gli uniform della luce che il corpo dichiara anche lui (la luce in corpo), nell'ordine dello shader. */
  private val BodyLightKeys = listOf(
    "rimWidth", "rimDark", "rimFeather", "rimLift", "reflexDir", "reflexWidths", "reflexStrength", "reflexDepth",
    "reflexFloor", "reflexHalo", "glowCenter", "glowRadius", "glow", "glowFill", "lightColor",
  )

  /**
   * Il corpo dichiara anche la luce in corpo (spenta a riposo, `lightInBody` 0, coi numeri della luce) e l'ombra in
   * viaggio (alfa zero): lo scrittore delle scene ferme non li scrive, perché il banco fermo li tollera; il validatore
   * del movimento li chiede. A `lightInBody` 0 e alfa zero non si disegnano.
   */
  @Suppress("UNCHECKED_CAST")
  private fun completeBody(element: Map<String, Any?>) {
    val body = (element["body"] as? Map<String, Any?>)?.get("uniforms") as? MutableMap<String, Any?> ?: return
    val light = (element["light"] as? List<Map<String, Any?>>)?.firstOrNull()?.get("uniforms") as? Map<String, Any?>
    if (light != null) for (key in BodyLightKeys) if (key !in body && key in light) body[key] = light[key]
    body.putIfAbsent("shadowOffset", listOf(glassDp(0f), glassDp(0f)))
    body.putIfAbsent("shadowRadii", listOf(glassDp(0f), glassDp(0f)))
    body.putIfAbsent("shadowAlphas", listOf(0, 0))
    body.putIfAbsent("shadowColor", "#00000000")
  }
}
