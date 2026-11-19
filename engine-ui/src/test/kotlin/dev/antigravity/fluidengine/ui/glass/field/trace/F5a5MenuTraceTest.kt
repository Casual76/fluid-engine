package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.FluidContextScrimLevel
import dev.antigravity.fluidengine.ui.fluid.FluidContextScrimTuning
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOrder
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReverses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * F5A.5: le prove di movimento che il piano chiede per la chiusura di F5A («Come si verifica»), scritte dal codice di
 * oggi e accoppiate ai fotogrammi di Apple allineati sul `t0` di R4 (B1 a: apertura a f9148, chiusura a f9187; B1 b, il
 * menu sulla foto, a f13046; il render, classe C, a f9338 e f9393 e rallentato circa due volte, solo per la sagoma).
 *
 *  - `menu-apertura`: il menu dal tasto, in tre tempi, con le voci nella lente e il punto di luce (le scelte di F5A.2).
 *  - `menu-apertura-gruppo`: lo stesso dal gruppo di tre tasti, contro il menu sulla foto e il render.
 *  - `menu-chiusura`, `menu-chiusura-gruppo`: il congedo a menu fermo, con t = 0 alla richiesta.
 *  - `menu-interrotto`: la chiusura chiesta a meta' apertura, a 133 ms: quattro fotogrammi di Apple, cosi' la chiusura
 *    del telefono si mette accanto alla nostra dal suo primo fotogramma (`t0Frame` = 9187 − 4).
 *  - `contestuale-apre`: il menu contestuale di una riga, col livello di sfondo di serie (B).
 *
 * I costruttori sono quelli di F5A.2 ([buildMenuTrace]) e di F5A.4 ([F5a4ContextTraces]): qui cambiano la testa (i
 * blocchi `apple`, le sonde della chiusura, l'evento dell'interruzione) e il nome. Le sonde sui pixel sono il metodo
 * degli slit-scan di R4 (`riferimenti/tools/scan.py`): il bordo alto lungo una colonna e la larghezza lungo una riga.
 * Niente si scrive senza `GLASS_TRACE_DIR`.
 */
internal object F5a5MenuTraces {
  const val Phase = "f5a-corr"

  /** Il fotogramma di Apple che vale t = 0, dalle tabelle di R4 (B1). */
  const val AppleOpenT0 = 9148
  const val AppleCloseT0 = 9187
  const val ApplePhotoT0 = 13046
  const val AppleRenderOpenT0 = 9338
  const val AppleRenderCloseT0 = 9393

  /** L'interruzione: quattro fotogrammi di Apple (133,5 ms), l'ottavo a 60 Hz. */
  const val InterruptMs = 133
  const val InterruptAtMs = 133.333

  /** Il ritaglio del telefono di B1 (a) (1,9 px del video per punto, l'aspetto della tela di `OggiMenuApertura`). */
  private val PhoneCrop = listOf(559, 280, 570, 481)

  /** Il ritaglio del render di B1 (c): il gruppo di quattro tasti e il menu, con lo stesso aspetto della tela. */
  private val RenderCrop = listOf(340, 160, 1000, 844)

  /** Centri del tasto e del gruppo nella tela ritagliata di OggiMenuApertura. */
  private val ButtonCenterX = 24.0 + FluidMetrics.GlassButtonLens.value / 2
  private val GroupCenterX = ButtonCenterX + FluidMetrics.GroupItemPitch.value
  private val ButtonCenterY = 221.0 - FluidMetrics.GlassButtonLens.value / 2

  /** Il pannello di quattro righe (6 + 4 x 48 + 6 dp): la misura su cui si fa la percentuale. */
  private const val PanelHeightDp = 204.0

  private fun phone(name: String, t0: Int, collage: String, note: String) = linkedMapOf<String, Any?>(
    "name" to name,
    "video" to "wwdc2025-219_hd",
    "t0Frame" to t0,
    "crop" to PhoneCrop,
    "collage" to "artifacts/liquid-glass/riferimenti/collage/$collage",
    "note" to note,
  )

  private val PhoneOpen = phone(
    "telefono", AppleOpenT0, "B1_menu_telefono_apertura_f9143-9166.jpg",
    "R4 B1 (a), classe A: il tasto in basso a sinistra diventa il menu; t = 0 a f9148 come la tabella di R4.",
  )

  private val PhoneClose = phone(
    "telefono", AppleCloseT0, "B1_menu_telefono_chiusura_f9185-9200.jpg",
    "R4 B1 (a), classe A: la chiusura (~200 ms di sagoma, l'icona nitida a ~300 ms); t = 0 a f9187 come R4.",
  )

  private val Photo = linkedMapOf<String, Any?>(
    "name" to "foto",
    "video" to "wwdc2025-219_hd",
    "t0Frame" to ApplePhotoT0,
    "crop" to listOf(500, 150, 910, 767),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_foto_f13036-13075.jpg",
    "note" to "R4 B1 (b), classe B: la capsula di tre tasti diventa un menu di sette righe sulla foto (il collage parte dieci fotogrammi prima).",
  )

  private fun render(t0: Int, collage: String, note: String) = linkedMapOf<String, Any?>(
    "name" to "render",
    "video" to "wwdc2025-219_hd",
    "t0Frame" to t0,
    "crop" to RenderCrop,
    "speed" to 2,
    "collage" to "artifacts/liquid-glass/riferimenti/collage/$collage",
    "note" to note,
  )

  /**
   * Le sonde di un viaggio che torna al tasto: le colonne e le righe al centro del pannello perdono il vetro quando la
   * sagoma rientra, quindi si misura lungo la colonna e la riga che passano per il tasto (o per il gruppo), dove il
   * vetro c'e' dall'inizio alla fine. La percentuale e' sull'altezza del pannello, come all'apertura. Tutto
   * cio' che cambia conta (`abs`, come le sonde dell'apertura): il tasto a riposo schiarisce la pagina chiara meno della
   * soglia, e la sola parte chiara (`light`) lo perderebbe.
   */
  private fun homeProbes(centerX: Double, ref: Map<String, Any?>?): List<Map<String, Any?>> = listOf(
    linkedMapOf<String, Any?>(
      "name" to "bordo-alto",
      "kind" to "edge",
      "line" to "v:$centerX:0:253.0",
      "which" to "first",
      "percentOf" to PanelHeightDp,
      "label" to "il bordo alto del vetro lungo la colonna del tasto, dai pixel; in % dell'altezza del pannello",
    ).also { if (ref != null) it["ref"] = ref },
    linkedMapOf<String, Any?>(
      "name" to "larghezza",
      "kind" to "width",
      "line" to "h:$ButtonCenterY:0:300.0",
      "label" to "la larghezza del vetro lungo la riga del tasto, dai pixel",
    ).also { if (ref != null) it["ref"] = ref },
  )

  /** Il segno della chiusura di Apple sul grafico: a 200 ms resta la bolla sul tasto (R4 B1 a). */
  private val CloseEvents = listOf(
    linkedMapOf<String, Any?>("t" to 200, "kind" to "mark", "label" to "R4 B1 (a): a 200 ms resta la bolla sul tasto"),
  )

  private val CloseRef = linkedMapOf<String, Any?>(
    "restMs" to listOf(167, 233),
    "source" to "R4 B1 (a), chiusura: la sagoma crolla fra 67 e 133 ms e a 200 ms resta la bolla sul tasto; il ritorno rapido dell'engine (rigidezza 900) e' la scelta di Alessio",
  )

  private fun retitled(
    built: GlassTrace,
    name: String,
    note: String,
    apple: List<Map<String, Any?>>,
    probes: List<Map<String, Any?>>? = null,
    events: List<Map<String, Any?>>? = null,
  ): GlassTrace {
    val head = LinkedHashMap(built.head)
    head["_"] = note
    head["apple"] = apple
    if (probes != null) head["probes"] = probes
    if (events != null) head["events"] = events
    return GlassTrace(name, Phase, built.density, built.theme, built.widthDp, built.heightDp, built.frames, built.fps, head)
  }

  private fun menu(variant: MenuTraceVariant, name: String): MenuBuilt {
    val automatic = fluidMenuReverses(FluidMenuOrder.Automatic, opensUpward = true, fromButton = true)
    return buildMenuTrace(
      OggiMenuApertura.scene(
        density = 1.5f,
        lensDp = FluidMetrics.GlassButtonLens.value,
        iconDp = FluidMetrics.BarIconSize.value,
        reversed = automatic,
      ),
      dress = TraceDress.Liquid, name = name, phase = Phase, unified = true, variant = variant.copy(corrected = true),
    )
  }

  /** Le sei tracce, con i fatti che il test controlla. */
  fun build(): List<Pair<GlassTrace, MenuBuilt?>> {
    val source = "F5A correzione: il menu Liquid dopo il giro di correzione, numeri dalle funzioni " +
      "dell'engine, orologio e assemblaggio ricostruiti nel test (OggiMenuApertura.kt). Senza bordo, ombra di oggi, banda fra " +
      "le sezioni e scrim della pagina."
    val opening = menu(MenuTraceVariant(pointOfLight = true), "menu-apertura")
    val openingGroup = menu(MenuTraceVariant(grouped = true, pointOfLight = true), "menu-apertura-gruppo")
    val closing = menu(MenuTraceVariant(closingAtMs = 700, alignToClose = true), "menu-chiusura")
    val closingGroup = menu(MenuTraceVariant(grouped = true, closingAtMs = 700, alignToClose = true), "menu-chiusura-gruppo")
    val interrupted = menu(MenuTraceVariant(closingAtMs = InterruptMs, pointOfLight = true), "menu-interrotto")
    val contextScenes = FluidContextScrimLevel.entries.associateWith {
      F5a4ContextTraces.build(it, dark = false, corrected = true)
    }
    val context = contextScenes.getValue(FluidContextScrimTuning.Default).opens

    @Suppress("UNCHECKED_CAST")
    val openEvents = opening.trace.head["events"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val groupProbes = openingGroup.trace.head["probes"] as List<Map<String, Any?>>
    return listOf(
      retitled(opening.trace, "menu-apertura", "$source Il tasto in basso a sinistra.", listOf(PhoneOpen)) to opening,
      retitled(
        openingGroup.trace, "menu-apertura-gruppo", "$source Il gruppo di tre tasti diventa il menu.",
        listOf(
          Photo,
          render(AppleRenderOpenT0, "B1_menu_apertura_render_f9336-9367.jpg", "R4 B1 (c), classe C: render circa 2 volte piu' lento, solo per la sagoma."),
        ),
        probes = groupProbes + linkedMapOf<String, Any?>(
          "name" to "larghezza-gruppo",
          "kind" to "width",
          "line" to "h:$ButtonCenterY:0:300.0",
              "label" to "la capsula del gruppo e poi il corpo, lungo la riga dei tasti: R4 B1 (b) la vuole al 60% a 33 ms",
        ),
      ) to openingGroup,
      retitled(closing.trace, "menu-chiusura", "$source Il congedo a menu fermo, t = 0 alla richiesta.", listOf(PhoneClose), homeProbes(ButtonCenterX, CloseRef), CloseEvents) to closing,
      retitled(
        closingGroup.trace, "menu-chiusura-gruppo", "$source Il congedo del menu del gruppo, t = 0 alla richiesta.",
        listOf(render(AppleRenderCloseT0, "B1_menu_chiusura_render_f9391-9414.jpg", "R4 B1 (c), classe C: render circa 2 volte piu' lento, solo per la sagoma.")),
        homeProbes(GroupCenterX, null),
        CloseEvents,
      ) to closingGroup,
      retitled(
        interrupted.trace, "menu-interrotto", "$source La chiusura chiesta a $InterruptMs ms, a meta' apertura.",
        listOf(
          PhoneOpen + ("name" to "apertura"),
          phone(
            "chiusura", AppleCloseT0 - 4, "B1_menu_telefono_chiusura_f9185-9200.jpg",
            "R4 B1 (a): la chiusura di Apple messa al nostro congedo (f9187 a $InterruptMs ms). Apple non ha un menu interrotto: " +
              "fino all'interruzione vale la ripresa `apertura`, da li' in poi questa.",
          ),
        ),
        homeProbes(ButtonCenterX, null),
        openEvents.filter { it["kind"] != "mark" } + linkedMapOf(
          "t" to InterruptAtMs, "kind" to "interrupt", "label" to "congedo a meta' apertura",
        ),
      ) to interrupted,
      retitled(
        context, "contestuale-apre",
        "F5A correzione: il menu contestuale di una riga col livello di sfondo di serie (C) e l'inchiostro nella lente, " +
          "(F5a4ContextTraces.kt). Senza luce del dito ne' banda fra le sezioni.",
        listOf(
          linkedMapOf(
            "name" to "telefono",
            "video" to "wwdc2025-219_hd",
            "t0Frame" to AppleOpenT0,
            "crop" to listOf(590, 0, 600, 760),
            "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
            "note" to "R4 B1 (a): il menu dal tasto del telefono, t = 0 a f9148. Solo per il menu: il contestuale di Apple non c'e' (R1 X1).",
          ),
        ),
      ) to null,
    ) + contextScenes.map { (level, built) ->
      retitled(
        built.closes, "contestuale-chiude-${level.name.lowercase()}",
        "F5A correzione: nel ritorno della riga il materiale cede il bordo alla registrazione.",
        listOf(PhoneClose + ("note" to "Menu dal tasto di R4 B1 (a), riferimento affine per l'inchiostro.")),
      ) to null
    }
  }
}

class F5a5MenuTraceTest {
  @Test
  fun theMotionProofsOfF5aCarryAppleAlignedOnR4AndWriteWhenAsked() {
    val traces = F5a5MenuTraces.build()
    assertEquals(
      listOf("menu-apertura", "menu-apertura-gruppo", "menu-chiusura", "menu-chiusura-gruppo", "menu-interrotto", "contestuale-apre",
        "contestuale-chiude-a", "contestuale-chiude-b", "contestuale-chiude-c"),
      traces.map { it.first.name },
    )
    for ((trace, built) in traces) {
      val name = trace.name
      assertEquals("$name: fase", F5a5MenuTraces.Phase, trace.phase)
      if (built != null) {
        assertTrue("$name: goccia", built.ringFrames > 0)
        assertTrue("$name: la simulazione ha integrato", built.integratedFrames > 0)
      }
      val apple = (trace.head["apple"] as List<*>).filterIsInstance<Map<*, *>>()
      assertTrue("$name: almeno un confronto con Apple", apple.isNotEmpty())
      assertEquals("$name: nomi delle riprese unici", apple.size, apple.map { it["name"] }.toSet().size)
      assertTrue("$name: piani e uniform serializzabili", trace.encode().isNotEmpty())
    }
    val byName = traces.associate { it.first.name to it.first }

    // L'apertura: inchiostro e lente al primo fotogramma (F5A-b), e Apple a f9148.
    for (name in listOf("menu-apertura", "menu-apertura-gruppo", "menu-interrotto")) {
      val first = requireNotNull(byName[name]).frames.first { it.tMs >= 0.0 }.elements.first { it["name"] == "menu" }
      val content = (first["content"] as List<*>).filterIsInstance<Map<*, *>>()
      assertTrue("$name: inchiostro e lente al primo fotogramma", content.any { it["name"] == "voci" && it["effect"] != null && (it["alpha"] as Float) > 0f })
    }
    val contextMenu = requireNotNull(byName["contestuale-apre"]).frames
      .firstNotNullOf { frame -> frame.elements.firstOrNull { it["name"] == "menu" } }
    assertTrue((contextMenu["content"] as List<*>).filterIsInstance<Map<*, *>>().any {
      it["name"] == "voci" && it["effect"] != null && (it["alpha"] as Float) > 0f
    })
    for (name in listOf("menu-chiusura", "menu-chiusura-gruppo")) {
      for (frame in requireNotNull(byName[name]).frames.filter { it.tMs >= 0.0 }) {
        val window = frame.elements.first { it["name"] == "menu" }
        val layers = (window["content"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val ink = layers.firstOrNull { it["name"] == "voci" }
        if (frame.tMs in 33.0..34.0) assertTrue((ink?.get("blurDp") as? Float ?: 0f) > 2f)
        if (frame.tMs >= 90.0) assertTrue(ink == null)
        if (ink != null) assertTrue(layers.filter {
          it["name"] == "faccia" || (it["name"] as? String)?.startsWith("tasto-") == true
        }.none { (it["alpha"] as? Float ?: 0f) > 0f })
      }
    }
    fun t0(name: String, ripresa: String) = (requireNotNull(byName[name]).head["apple"] as List<*>)
      .filterIsInstance<Map<*, *>>().first { it["name"] == ripresa }["t0Frame"]
    assertEquals(F5a5MenuTraces.AppleOpenT0, t0("menu-apertura", "telefono"))
    assertEquals(F5a5MenuTraces.AppleCloseT0, t0("menu-chiusura", "telefono"))
    assertEquals(F5a5MenuTraces.AppleCloseT0 - 4, t0("menu-interrotto", "chiusura"))
    assertEquals(F5a5MenuTraces.AppleOpenT0, t0("contestuale-apre", "telefono"))

    // La chiusura comincia a t = 0 (la richiesta), e l'interruzione cade su un fotogramma della traccia.
    assertTrue(requireNotNull(byName["menu-chiusura"]).frames.any { kotlin.math.abs(it.tMs) < 1e-9 })
    val interrupted = requireNotNull(byName["menu-interrotto"])
    val interrupt = (interrupted.head["events"] as List<*>).filterIsInstance<Map<*, *>>().single { it["kind"] == "interrupt" }
    assertTrue(interrupted.frames.any { kotlin.math.abs(it.tMs - (interrupt["t"] as Double)) < 1e-2 })

    for ((trace, _) in traces) GlassTraceOutput.writeIfRequested(trace)
  }
}
