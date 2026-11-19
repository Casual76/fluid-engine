package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOrder
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReverses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tracce del menu dal tasto e dal gruppo dopo la sessione «contestuale», fase `tasto`: il tasto singolo contro B1 (a)
 * (WWDC25 219 a 5:06, f9148), il gruppo contro la graffetta B1 (b) (7:15, f13046), il congedo contro la chiusura di B1 (a)
 * (f9187), in chiaro e in scuro. Il costruttore e la scena sono quelli di F5A.5 ([buildMenuTrace],
 * [OggiMenuApertura.scene]) a densita' 1,5, con l'ordine `Automatic` di Scelte A.
 *
 * In piu' (solo dopo): `apre-graffetta`, il gruppo col tasto premuto a sinistra come la graffetta di Apple (l'uovo storto
 * con la coda verso il tasto si vede meglio), e `interrotto-gruppo`, il congedo a 200 ms, a meta' nascita.
 */
internal object TastoTraces {
  const val Phase = "tasto"

  /** B1 (a): il tasto in basso a sinistra del telefono che diventa il menu, t = 0 al primo fotogramma che si muove. */
  val PhoneOpen = linkedMapOf<String, Any?>(
    "name" to "telefono",
    "video" to "wwdc2025-219_hd",
    "t0Frame" to 9148,
    "crop" to listOf(559, 280, 570, 481),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
    "note" to "R4 B1 (a), classe A: il tasto in basso a sinistra diventa il menu. Il modello del menu dal tasto singolo.",
  )

  /**
   * I contorni di B1 (a) (`FluidButtonBirthProfile`): ms, bordo vicino, bordo lontano, sinistro, destro, in frazioni del
   * menu a riposo (601-932 × 318-700 px del video HD).
   */
  val AppleButton = listOf(
    doubleArrayOf(33.4, 0.058, 0.644, 0.021, 0.603),
    doubleArrayOf(66.7, 0.052, 0.866, 0.012, 0.814),
    doubleArrayOf(100.1, 0.055, 0.987, 0.015, 0.959),
    doubleArrayOf(133.5, 0.052, 1.063, 0.027, 1.035),
    doubleArrayOf(166.8, 0.044, 1.076, 0.029, 1.062),
    doubleArrayOf(200.2, 0.030, 1.064, 0.023, 1.059),
    doubleArrayOf(233.6, 0.021, 1.045, 0.017, 1.044),
    doubleArrayOf(266.9, 0.009, 1.022, 0.012, 1.033),
    doubleArrayOf(300.3, 0.005, 1.003, 0.006, 1.014),
    doubleArrayOf(333.7, 0.003, 1.003, 0.003, 1.005),
    doubleArrayOf(367.0, 0.0, 1.0, 0.0, 1.0),
  )

  /** Il menu di quattro righe nel fondo scuro di F1B.2. */
  private val DarkPage = linkedMapOf<String, Any?>("kind" to "color", "color" to "#1C1C1E")

  private fun menu(variant: MenuTraceVariant, name: String, dark: Boolean): MenuBuilt {
    val automatic = fluidMenuReverses(FluidMenuOrder.Automatic, opensUpward = true, fromButton = true)
    return buildMenuTrace(
      OggiMenuApertura.scene(
        density = 1.5f,
        lensDp = FluidMetrics.GlassButtonLens.value,
        iconDp = FluidMetrics.BarIconSize.value,
        reversed = automatic,
      ),
      dress = TraceDress.Liquid, name = name, phase = Phase, dark = dark, unified = true,
      background = if (dark) DarkPage else null,
      variant = variant.copy(corrected = true),
    )
  }

  private fun retitled(built: MenuBuilt, note: String, apple: Map<String, Any?>): GlassTrace {
    val t = built.trace
    val head = LinkedHashMap(t.head)
    head["_"] = note
    head["apple"] = listOf(apple)
    // Le sonde di OggiMenuApertura parlano del confronto di F0.5: qui il confronto e' la tavola fase per fase.
    head.remove("probes")
    head.remove("events")
    return GlassTrace(t.name, Phase, t.density, t.theme, t.widthDp, t.heightDp, t.frames, t.fps, head)
  }

  /** Le tracce, con i fatti che il test controlla. */
  fun build(): List<Pair<GlassTrace, MenuBuilt>> {
    val source = "Sessione contestuale, fase tasto: il menu Liquid dal tasto e dal gruppo, numeri dalle funzioni " +
      "dell'engine, orologio e assemblaggio ricostruiti nel test (OggiMenuApertura.kt). Senza bordo, banda fra le sezioni " +
      "e scrim della pagina."
    val out = ArrayList<Pair<GlassTrace, MenuBuilt>>()
    for (dark in listOf(false, true)) {
      val s = if (dark) "-scuro" else ""
      val side = if (dark) " Scuro." else " Chiaro."
      fun add(variant: MenuTraceVariant, name: String, note: String, apple: Map<String, Any?>) {
        val built = menu(variant, name + s, dark)
        out += retitled(built, "$source $note$side", apple) to built
      }
      add(MenuTraceVariant(pointOfLight = true), "apre-tasto", "Il tasto in basso a sinistra diventa il menu (B1 a).", PhoneOpen)
      add(MenuTraceVariant(grouped = true, pointOfLight = true), "apre-gruppo",
        "Il gruppo di tre tasti diventa il menu dal tasto di destra (la graffetta, B1 b).", ContestualeTraces.Photo)
      add(MenuTraceVariant(closingAtMs = 900, alignToClose = true), "chiude-tasto",
        "Il congedo a menu fermo, t = 0 alla richiesta.", ContestualeTraces.PhoneClose)
      add(MenuTraceVariant(grouped = true, closingAtMs = 900, alignToClose = true), "chiude-gruppo",
        "Il congedo del menu del gruppo a menu fermo, t = 0 alla richiesta.", ContestualeTraces.PhoneClose)
    }
    val paperclip = menu(MenuTraceVariant(grouped = true, pointOfLight = true, triggerAtStart = true), "apre-graffetta", dark = false)
    out += retitled(
      paperclip,
      "$source Il gruppo diventa il menu dal tasto di sinistra, come la graffetta di Apple (B1 b). Chiaro.",
      ContestualeTraces.Photo,
    ) to paperclip
    val interrupted = menu(MenuTraceVariant(grouped = true, closingAtMs = 200, alignToClose = true), "interrotto-gruppo", dark = false)
    out += retitled(
      interrupted,
      "$source Il congedo del menu del gruppo a 200 ms, a meta' nascita: t = 0 alla richiesta. Chiaro.",
      ContestualeTraces.PhoneClose,
    ) to interrupted
    return out
  }

  /** Bordo vicino, lontano, sinistro e destro del corpo [body] in frazioni del pannello [panel], che si apre verso l'alto. */
  fun fractions(panel: Rect, body: Rect): DoubleArray = doubleArrayOf(
    ((panel.bottom - body.bottom) / panel.height).toDouble(),
    ((panel.bottom - body.top) / panel.height).toDouble(),
    ((body.left - panel.left) / panel.width).toDouble(),
    ((body.right - panel.left) / panel.width).toDouble(),
  )
}

class TastoTraceTest {
  @Test
  fun theButtonAndGroupMenusAreBornLikeApplesAndWriteWhenAsked() {
    val traces = TastoTraces.build()
    assertEquals(
      listOf("apre-tasto", "apre-gruppo", "chiude-tasto", "chiude-gruppo").let { it + it.map { n -> "$n-scuro" } } +
        listOf("apre-graffetta", "interrotto-gruppo"),
      traces.map { it.first.name },
    )
    // Si scrivono prima delle verifiche: una traccia che non passa si deve poter guardare sul banco.
    for ((trace, _) in traces) GlassTraceOutput.writeIfRequested(trace)
    for ((trace, built) in traces) {
      assertEquals(TastoTraces.Phase, trace.phase)
      assertTrue("${trace.name}: goccia", built.ringFrames > 0)
      assertTrue("${trace.name}: la nascita di Apple", built.born)
      assertTrue("${trace.name}: serializzabile", trace.encode().isNotEmpty())
      // Il costo: la nascita non aggiunge superfici ne' catture. Un elemento (il menu) e una cattura per fotogramma.
      for (frame in trace.frames) {
        assertTrue("${trace.name} a ${frame.tMs} ms: ${frame.elements.size} elementi", frame.elements.size <= 1)
      }
    }
    val byName = traces.associate { it.first.name to it.second }

    // Il tasto contro B1 (a): la sagoma vera (l'anello della goccia) a ogni fotogramma di Apple.
    val button = requireNotNull(byName["apre-tasto"])
    val rows = StringBuilder("ms;vicino banco;vicino Apple;lontano banco;lontano Apple;larghezza banco;larghezza Apple;sinistro banco;sinistro Apple\n")
    val measured = TastoTraces.AppleButton.map { a ->
      val (t, body) = button.openBounds.last { it.first <= a[0] + 1e-6 }
      val f = TastoTraces.fractions(button.panelPx, body)
      rows.append("%.1f (%.1f);%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f\n".format(a[0], t, f[0], a[1], f[1], a[2], f[3] - f[2], a[4] - a[3], f[2], a[3]))
      f
    }
    println("TASTO sagoma contro Apple B1 (a)\n$rows")

    // Il gruppo contro B1 (b): bordo vicino, lontano, larghezza, come il contestuale.
    for (name in listOf("apre-gruppo", "apre-graffetta")) {
      val built = requireNotNull(byName[name])
      val table = StringBuilder("ms;vicino banco;vicino Apple;lontano banco;lontano Apple;larghezza banco;larghezza Apple\n")
      for (a in ContestualeTraces.Apple) {
        val (t, body) = built.openBounds.last { it.first <= a[0] + 1e-6 }
        val f = TastoTraces.fractions(built.panelPx, body)
        table.append("%.1f (%.1f);%.3f;%.3f;%.3f;%.3f;%.3f;%.3f\n".format(a[0], t, f[0], a[1], f[1], a[2], f[3] - f[2], a[3]))
      }
      println("GRUPPO $name sagoma contro Apple B1 (b)\n$table")
    }
    // Quanto si vede lontana una voce dal suo riquadro di scelta durante la nascita (il dito la riporta indietro con
    // `FluidMenuSelection.pointerMap`): il tasto, il gruppo, e il contestuale per confronto.
    val context = F5a4ContextTraces.build(dev.antigravity.fluidengine.ui.fluid.FluidContextScrimTuning.Default, dark = false, corrected = true)
    val shifts = linkedMapOf(
      "tasto" to button.rowShift,
      "gruppo" to requireNotNull(byName["apre-gruppo"]).rowShift,
      "graffetta" to requireNotNull(byName["apre-graffetta"]).rowShift,
      "contestuale" to context.rowShift,
    )
    val shiftTable = StringBuilder("ms;" + shifts.keys.joinToString(";") + "\n")
    var ms = 0.0
    while (ms <= 900.0) {
      shiftTable.append("%.0f".format(ms))
      for (list in shifts.values) {
        val v = list.lastOrNull { it.first <= ms + 1e-6 }?.second
        shiftTable.append(";").append(if (v == null) "-" else "%.1f".format(v))
      }
      shiftTable.append("\n")
      ms += 33.367
    }
    println("SCARTO voce visibile contro riquadro di scelta (dp)\n$shiftTable")
    for ((name, list) in shifts) println("SCARTO massimo $name: ${"%.1f".format(list.maxOfOrNull { it.second } ?: 0f)} dp")

    for (name in listOf("chiude-tasto", "chiude-gruppo", "interrotto-gruppo")) {
      val built = requireNotNull(byName[name])
      val table = StringBuilder("ms;vicino;lontano;sinistro;destro\n")
      for ((t, body) in built.closeBounds.filter { it.first >= 0.0 }) {
        val f = TastoTraces.fractions(built.panelPx, body)
        table.append("%.1f;%.3f;%.3f;%.3f;%.3f\n".format(t, f[0], f[1], f[2], f[3]))
      }
      println("CONGEDO $name\n$table")
    }

    // Vicino ad Apple, non uguale: a ogni fotogramma di B1 (a) da 67 ms il bordo lontano e la larghezza entro il 6% del menu,
    // il vicino entro il 5%. Il lato del tasto (sul margine) resta fermo: in Apple il menu si sposta anche di traverso del 2-3%.
    for ((i, a) in TastoTraces.AppleButton.withIndex()) {
      if (a[0] < 60.0) continue
      val f = measured[i]
      assertEquals("tasto: lontano a ${a[0]} ms", a[2], f[1], 0.06)
      assertEquals("tasto: larghezza a ${a[0]} ms", a[4] - a[3], f[3] - f[2], 0.06)
      assertEquals("tasto: vicino a ${a[0]} ms", a[1], f[0], 0.05)
    }
    // Il gruppo: a 100 ms il menu e' meno di meta' altezza, come la graffetta (0,35); pieno solo verso 334 ms.
    val group = requireNotNull(byName["apre-gruppo"])
    val at100 = group.openBounds.last { it.first <= 100.0 }.second
    assertTrue("gruppo: a 100 ms alto ${at100.height / group.panelPx.height}", at100.height / group.panelPx.height < 0.5f)
    // Il tasto: a 100 ms quasi pieno, come B1 (a) (0,93).
    val b100 = button.openBounds.last { it.first <= 100.0 }.second
    assertTrue("tasto: a 100 ms alto ${b100.height / button.panelPx.height}", b100.height / button.panelPx.height > 0.75f)
  }
}
