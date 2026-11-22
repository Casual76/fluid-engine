package dev.antigravity.fluidengine.ui.fluid.activity

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.translate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.calculateGlassIntensity
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdgeDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdgeSide
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdgeStyle
import dev.antigravity.fluidengine.ui.fluid.fluidScrollEdgeProfile
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMinimizeLook
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarRetractingAfter
import dev.antigravity.fluidengine.ui.glass.field.glassScrollEdgeUniforms
import dev.antigravity.fluidengine.ui.fluid.FluidModalSourceMotion
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidMenuInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLensBlurDp
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReturnInkSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidModalFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalFocusSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalGeometrySpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalInkExitSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalReturnFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalSourceSlab
import dev.antigravity.fluidengine.ui.fluid.fluidModalTravelClip
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDeclaration
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMemory
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMotion
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarResolved
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarGlassContentKey
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarGlassTransition
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarSoftMaterialize
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbarFitting
import dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDp
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.trace.F38aTraces
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceJson
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.LiquidPhysicsDress
import dev.antigravity.fluidengine.ui.glass.field.trace.TodayLiquidDriver
import dev.antigravity.fluidengine.ui.glass.field.trace.TodaySpring
import dev.antigravity.fluidengine.ui.glass.field.trace.menuTraceContentLens
import dev.antigravity.fluidengine.ui.glass.field.trace.round3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Le tracce del banco della pillola di attività (F6B.10), a 120 Hz, **dal codice di produzione**:
 *
 * - la barra è il modello vero ([buildFluidToolbar], [fluidToolbarWithActivityOf], [resolveFluidToolbarFitting]) sul
 *   piano vero (`F38aTraces`: il conducente del contenitore, la comparsa e l'uscita per lente, il contenuto della lente);
 * - il titolo che cede il posto ha la curva di `fluidTitleHandoffLayer` (R7, chi esce), all'andata e al ritorno;
 * - l'esito è lo scambio sfocato di R7 ([GlassContentJourney]) col battito di [FluidActivityProfile];
 * - la card è il popover dalla sorgente di F5B.1 (gli stessi costruttori di `F5b1ModalTraceTest`), con la pillola come
 *   sorgente: la pillola si raccoglie in goccia e la card si apre attorno a lei, poi ci rientra.
 *
 * Nel banco l'anello del lavoro è la pista e un arco di punti (lo strumento non ha l'arco); sul dispositivo è un arco vero.
 *
 * Si scrivono con `GLASS_F6B10_DIR` (la cartella delle fasi), fase `f6b-10`; le tracce chiare e quelle `-scuro`.
 */
class FluidActivityTraceTest {

  private data class Timeline(
    val name: String,
    val note: String,
    /** Quando la pillola compare; null: c'è già. */
    val appearAt: Double? = null,
    val tapAt: Double? = null,
    val closeAt: Double? = null,
    val outcomeAt: Double? = null,
    val exitAt: Double? = null,
    val largeTitle: Boolean = false,
    val lastFrame: Int,
    val heightDp: Float = 330f,
    val apple: List<Map<String, Any?>>? = null,
    /** Quando il dito comincia a scorrere la lista in su (la barra si ritira, F4A.4); null: la lista non scorre. */
    val scrollAt: Double? = null,
    /** Quando il dito la riporta in giù (la barra torna). */
    val returnAt: Double? = null,
  )

  private val hold = FluidActivityProfile.SuccessHoldMs.toDouble()

  private val timelines = listOf(
    Timeline("attivita-compare", "una sincronizzazione parte: la pillola compare per lente al posto del titolo, che esce sfocato",
      appearAt = 0.0, lastFrame = 72, apple = listOf(Appear)),
    Timeline("attivita-esce", "la pillola passa il testimone (un tasto dice l'esito): esce per lente e il titolo torna",
      exitAt = 0.0, lastFrame = 72, apple = listOf(Disappear)),
    Timeline("attivita-esito", "la sincronizzazione finisce: la spunta entra sfocata, un battito, poi la pillola esce e il titolo torna",
      outcomeAt = 0.0, exitAt = hold, lastFrame = ((hold + 560.0) * 0.12).toInt()),
    Timeline("attivita-estesa", "al tocco la card nasce dalla pillola (il popover dalla sorgente di F5B.1) e ci rientra",
      tapAt = 0.0, closeAt = 800.0, lastFrame = 140),
    Timeline("attivita-titolo-grande", "col titolo grande: la pillola sta nella barra al posto del titolo in linea, il titolo grande resta sotto",
      appearAt = 0.0, largeTitle = true, lastFrame = 72),
    Timeline("attivita-alessio", "il viaggio intero: la pillola prende il posto del titolo e si apre in card al tocco; " +
      "la lista scorre e la barra si ritira, della pillola resta l'anellino in alto; la barra torna e la pillola torna " +
      "compatta; il lavoro finisce, la pillola esce e il titolo torna",
      appearAt = 0.0, tapAt = 800.0, closeAt = 1_700.0, scrollAt = 2_400.0, returnAt = 3_700.0,
      outcomeAt = 4_800.0, exitAt = 4_800.0 + hold, lastFrame = ((4_800.0 + hold + 600.0) * 0.12).toInt(), heightDp = 360f),
    Timeline("attivita-anellino", "la lista scorre e la barra si ritira: della pillola resta l'anellino, piccolo, in alto; " +
      "la barra torna e la pillola torna compatta",
      scrollAt = 100.0, returnAt = 1_400.0, lastFrame = 290, heightDp = 360f),
  )

  @Test fun benchTracesAt120Hz() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B10_DIR"))
    for (timeline in timelines) for (dark in listOf(false, true)) {
      val trace = build(timeline, dark)
      assertEquals(120, trace.fps)
      // La lista che scorre sta nella pagina di ogni fotogramma (TRACE.md, `page` del fotogramma): il vetro e il bordo
      // dello scorrimento catturano la pagina, non gli elementi.
      val pages = framePages
      val json = LinkedHashMap(trace.toJson())
      if (pages != null) json["frames"] = trace.frames.mapIndexed { index, frame ->
        linkedMapOf("t" to frame.tMs, "page" to pages[index], "elements" to frame.elements)
      }
      val text = GlassTraceJson.encode(json)
      assertTrue(text.isNotEmpty())
      if (output != null) GlassTraceOutput.fileFor(output, trace).apply { parentFile?.mkdirs() }.writeText(text, Charsets.UTF_8)
    }
  }

  @Test fun theBarOfTheTracesIsTheProductionModel() {
    // La pillola delle tracce è quella della barra vera: al centro, larga quanto la regola, per lente.
    val (bar, _) = resolved(withActivity = true, outcome = null)
    val pill = bar.element(FluidActivityIds.Pill)!!
    assertEquals(PillWidth, pill.widthDp)
    assertEquals(FluidGlassTransition.MaterializeWhenAlone, fluidToolbarGlassTransition(bar.groups.single { it.id == pill.groupId }))
    // Terzo giro: con la comparsa morbida; gli altri vetri no.
    assertTrue(fluidToolbarSoftMaterialize(bar.groups.single { it.id == pill.groupId }))
    assertTrue(bar.groups.filter { it.id != pill.groupId }.none { fluidToolbarSoftMaterialize(it) })
    // Al centro del posto libero: fra l'indietro e il gruppo di destra, a 12 dp da quest'ultimo.
    val group = bar.groups.last()
    assertTrue(pill.leftDp + pill.widthDp <= group.leftDp - 12f + 0.01f)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La scena
  // ---------------------------------------------------------------------------------------------------------------

  private companion object {
    const val Width = 400f
    const val BarTop = 30f
    const val Density = 1f
    const val Phase = "f6b-10"
    const val Title = "Posta"
    const val Phrase = "Sincronizzo"
    const val Progress = 0.42f

    /** La frase misurata come la misura `FluidActivityPillStyles` (bodyMedium semibold, ≈7,8 dp a lettera). */
    val PhraseDp = Phrase.length * 7.8f

    /** La percentuale più larga, «100%», nella labelLarge tabulare. */
    const val ReadoutDp = 34f
    val PillWidth = fluidActivityPillWidthOf(PhraseDp, ReadoutDp, 0f)

    /** La riga della barra sotto la barra di stato ([BarTop]): da qui comincia la lista, come in `FluidScreen`. */
    val RowDp = FluidScreenDefaults.ControlRowHeight.value

    /** Dove comincia la lista: sotto la barra, col suo spazio (il padding in alto della lista della schermata). */
    val ListTop = BarTop + RowDp + 8f

    /** Il passo delle righe della posta e la loro altezza. */
    const val RowPitch = 70f
    const val RowHeight = 62f

    /** Quanto il dito scorre la lista, in dp, e in quanto tempo. */
    const val ScrollDp = 150f
    const val ScrollMs = 460.0

    /**
     * La corsa del ritiro di una schermata col titolo in linea: la riga dei controlli (`FluidScreen`: `travelPx` =
     * riga + titolo grande + spazio, qui solo la riga).
     */
    val TravelDp = FluidScreenDefaults.ControlRowHeight.value

    /** La densità a cui si rende la scena (`--density 2`): gli uniform in px del bordo dello scorrimento la vogliono. */
    const val RenderDensity = 2f

    /** Le riprese di Apple della comparsa per lente (R4 B3), come in `F33Traces`. */
    val Appear: Map<String, Any?> = linkedMapOf(
      "name" to "comparsa", "video" to "wwdc2025-219_hd", "t0Frame" to 5399, "crop" to listOf(442, 197, 1050, 700),
      "collage" to "artifacts/liquid-glass/riferimenti/collage/B3_comparsa_play_f5397-5420.jpg",
      "note" to "R4 B3, media: comparsa 430 ms, contorno prima, simbolo da 2x a 1x. Il Play di Apple è un tondo; la pillola " +
        "è una capsula larga: vale l'ordine delle fasi, non la sagoma.",
    )
    val Disappear: Map<String, Any?> = linkedMapOf(
      "name" to "scomparsa", "video" to "wwdc2025-219_hd", "t0Frame" to 5368, "crop" to listOf(442, 197, 1050, 700),
      "collage" to "artifacts/liquid-glass/riferimenti/collage/B3_comparsa_play_f5290-5400_ogni2.jpg",
      "note" to "R4 B3, media: scomparsa 470 ms, simbolo via in 130, rifrazione a zero entro 400, poi il contorno.",
    )
  }

  private val metrics = fluidToolbarMetrics()
  private val textWidth: (String) -> Float = { it.length * 8f + FluidToolbarDefaults.TextPadding.value * 2f }

  private fun declaration(): FluidToolbarDeclaration = buildFluidToolbar({}) {
    button("search", FluidIcons.Search, "Cerca", onClick = {})
    button("more", FluidIcons.MoreHoriz, "Altro", onClick = {})
  }

  /** La barra risolta, con o senza la pillola, e la sua dichiarazione. */
  private fun resolved(
    withActivity: Boolean,
    outcome: FluidActivityOutcome?,
    narrow: Boolean = false,
  ): Pair<FluidToolbarResolved, FluidToolbarDeclaration> {
    val base = declaration()
    val bar = if (!withActivity) base else fluidToolbarWithActivityOf(
      base, FluidActivityDisplay(FluidActivity.sync(progress = { Progress }), outcome), FluidActivityLabels(), PillWidth,
      FluidActivityReadout.Percent, minimalOnly = false,
    ) {}
    return resolveFluidToolbarFitting(fluidToolbarModel(bar, textWidth, title = Title), Width, metrics = metrics, narrow = narrow) to bar
  }

  /** Il piano della barra: un pezzo per gruppo, con l'identità del vetro di `FluidToolbarMemory`, come lo posa la barra. */
  private fun plane(memory: FluidToolbarMemory, resolved: FluidToolbarResolved, heightDp: Float): GlassPlaneState {
    memory.update(resolved, FluidToolbarMotion.Full)
    val h = metrics.buttonWidthDp
    val pieces = resolved.groups.map { group ->
      val id = memory.glassIds.getValue(group.id)
      val bounds = Rect(group.leftDp * Density, BarTop * Density, (group.leftDp + group.widthDp) * Density, (BarTop + h) * Density)
      val pill = group.items.any { it.id == FluidActivityIds.Pill }
      GlassPlanePiece(
        key = if (pill) PillKey else id.toString(), bounds = bounds, radii = FluidCornerRadii.all(bounds.height / 2f), id = id,
        transition = when (fluidToolbarGlassTransition(group)) {
          FluidGlassTransition.MaterializeWhenAlone -> GlassPlaneTransition.MaterializeWhenAlone
          else -> GlassPlaneTransition.MatchedGeometry
        },
        contentKey = fluidToolbarGlassContentKey(group),
        // F6B.10, terzo giro: la pillola compare e se ne va sul posto (`fluidToolbarSoftMaterialize`).
        softMaterialize = fluidToolbarSoftMaterialize(group),
      )
    }
    // Lo spazio di fusione del contenitore della barra (FluidGlassContainerDefaults.Spacing, 16 dp): i gruppi a 12 dp sono
    // un campo solo, come sul dispositivo.
    return GlassPlaneState(pieces, PhysicsPlaneFusionDp * Density, Rect(0f, 0f, Width * Density, heightDp * Density))
  }

  private object PillKey {
    override fun toString(): String = "pill"
  }

  private class Ink(dark: Boolean) {
    val page = if (dark) "#101217" else "#EEF1F6"
    val row = if (dark) "#22262F" else "#FFFFFF"
    val text = if (dark) "#F2F2F7" else "#17171A"
    val secondary = if (dark) "#9EA3AE" else "#6B7080"
    val accent = if (dark) "#0A84FF" else "#007AFF"
    val error = if (dark) "#FF453A" else "#FF3B30"
    val stripe = if (dark) listOf("#3A5BA0", "#7A3E8E", "#2F7D6B") else listOf("#7FA7F5", "#F3A1C8", "#8ED9C2")
  }

  /**
   * L'altezza della tela della traccia in corso: gli elementi che portano solo contenuto (il titolo, le icone, la faccia
   * della pillola) sono grandi quanto la tela, perché il banco ritaglia il contenuto sul riquadro del suo elemento.
   */
  private var canvas = 330f

  /** Le pagine dei fotogrammi della traccia appena costruita, se la lista scorre; null se la pagina è una sola. */
  private var framePages: List<List<Map<String, Any?>>>? = null

  private fun build(timeline: Timeline, dark: Boolean): GlassTrace {
    canvas = timeline.heightDp
    val ink = Ink(dark)
    val name = if (dark) "${timeline.name}-scuro" else timeline.name
    val height = timeline.heightDp
    val memory = FluidToolbarMemory()
    val (plain, _) = resolved(withActivity = false, outcome = null)
    val (running, _) = resolved(withActivity = true, outcome = null)
    val (done, _) = resolved(withActivity = true, outcome = FluidActivityOutcome.succeeded())
    val (slim, _) = resolved(withActivity = true, outcome = null, narrow = true)
    val planeWithout = plane(memory, plain, height)
    val planeWith = plane(memory, running, height)
    val planeDone = plane(memory, done, height)
    val planeSlim = plane(memory, slim, height)
    val pillRect = running.element(FluidActivityIds.Pill)!!.let { Rect(it.leftDp, BarTop, it.leftDp + it.widthDp, BarTop + metrics.buttonWidthDp) }
    val slimRect = slim.element(FluidActivityIds.Pill)!!.let { Rect(it.leftDp, BarTop, it.leftDp + it.widthDp, BarTop + metrics.buttonWidthDp) }
    // Il ritiro (F4A.4): la barra si stringe a metà corsa (`FluidToolbarDefaults.RetractingFrom`) e torna larga a zero.
    val retraction = Retraction(timeline)
    // Gli stati del piano nel tempo: la pillola che compare, che si stringe e torna larga col ritiro, l'esito (lo stesso
    // vetro), la pillola che se ne va.
    val initial = if (timeline.appearAt != null) planeWithout else planeWith
    val steps = buildList {
      timeline.appearAt?.let { add(F38aTraces.Step(it, planeWith)) }
      retraction.narrowAt?.let { add(F38aTraces.Step(it, planeSlim)) }
      retraction.wideAt?.let { add(F38aTraces.Step(it, planeWith)) }
      timeline.outcomeAt?.let { add(F38aTraces.Step(it, planeDone)) }
      timeline.exitAt?.let { add(F38aTraces.Step(it, planeWithout)) }
    }.sortedBy { it.atMs }
    fun planeTrace(start: GlassPlaneState, steps: List<F38aTraces.Step>) = F38aTraces.build(F38aTraces.Scene(
      name, timeline.note, Width, height, dark, start, steps,
      labels = mapOf(PillKey to F38aTraces.Label(text = Phrase)),
      lastFrame = timeline.lastFrame, firstFrame = -6, density = Density,
      phase = Phase, background = mapOf("kind" to "color", "color" to ink.page), apple = timeline.apple,
    ))
    val base = planeTrace(initial, steps)
    // Mentre la card c'è il vetro della pillola è ceduto alla card (il passaggio istantaneo del tasto solo): il piano è
    // quello senza la pillola, fermo. Gli altri vetri sono fermi in tutti e due, quindi il passaggio non si vede.
    val held = if (timeline.tapAt != null) planeTrace(planeWithout, emptyList()) else null

    // Gli scambi sfocati di R7 nello stesso vetro: la forma minima quando la barra si stringe, la compatta quando torna,
    // l'esito.
    val swaps = buildList {
      var journey = GlassContentJourney.rest(FaceRunning)
      for ((at, face) in listOfNotNull(retraction.narrowAt?.let { it to FaceMinimal }, retraction.wideAt?.let { it to FaceRunning },
        timeline.outcomeAt?.let { it to FaceDone }).sortedBy { it.first }) {
        journey = journey.retarget(at, face)
        add(at to journey)
      }
    }
    val card = timeline.tapAt?.let { CardJourney(pillRect, it, timeline.closeAt ?: Double.POSITIVE_INFINITY, ink, height, dark) }
    val ring = RingJourney(retraction, pillRect, slimRect)

    val pages = ArrayList<List<Map<String, Any?>>>()
    val frames = base.frames.mapIndexed { index, main ->
      val t = main.tMs
      val consumed = card?.holdsSource(t) == true
      val frame = if (consumed && held != null) held.frames[index] else main
      val elements = ArrayList<Map<String, Any?>>()
      pages += page(ink, timeline.largeTitle, height, retraction.scrollDp(t))
      // Il ritiro della barra (F4A.4): titolo, vetri, icone e pillola salgono, si sfocano e svaniscono insieme
      // (`fluidToolbarMinimizeLayer`); l'anellino no, sta fuori.
      val minimize = retraction.progress(t)
      // Il bordo dello scorrimento (F2.5) sopra la lista che ci scorre sotto, sotto la barra: lo shader di produzione,
      // con l'intensità della schermata (`calculateGlassIntensity`, almeno quanto il ritiro). A zero non si disegna.
      if (timeline.scrollAt != null) {
        val scroll = retraction.scrollDp(t)
        val amount = maxOf(minimize, calculateGlassIntensity(
          firstVisibleItemIndex = (scroll / RowPitch).toInt(), firstVisibleItemScrollOffset = (scroll % RowPitch).toInt(),
          collapseProgress = 0f, deadZonePx = FluidScreenDefaults.ScrollEdgeDeadZone.value,
          rampDistancePx = FluidScreenDefaults.ScrollEdgeRampDistance.value,
        ))
        if (amount > 0f) elements += scrollEdge(ink, amount)
      }
      val bar = ArrayList<Map<String, Any?>>()
      // Il titolo in linea che cede il posto e torna (fluidTitleHandoffLayer); col titolo grande non c'è in linea. A riposo
      // sta sotto i vetri della barra (si compone prima); mentre cede il posto sta sopra (terzo giro, `zIndex` in
      // `FluidToolbarTopRow`): il vetro della pillola mostra la pagina sotto di sé e lo coprirebbe.
      val title = if (timeline.largeTitle) null else titleElement(timeline, t, ink)
      val titleAbove = titleAboveBar(timeline, t)
      if (!titleAbove) title?.let(bar::add)
      // Il piano, senza il contenuto della pillola, che si scrive qui sotto (il piano ne dà l'aspetto della lente).
      var pillLook: PillLook? = null
      for (element in frame.elements) {
        @Suppress("UNCHECKED_CAST")
        val content = (element["content"] as? List<Map<String, Any?>>).orEmpty()
        val pillLayer = content.firstOrNull { it["name"] == "c-$PillKey" }
        if (pillLayer != null) {
          pillLook = PillLook.of(pillLayer, (element["x"] as Number).toFloat(), (element["y"] as Number).toFloat())
        }
        bar += element + ("content" to content.filter { it["name"] != "c-$PillKey" })
      }
      // Le icone ferme della barra: l'indietro e il gruppo di destra.
      bar += barIcons(running, ink)
      // La faccia della pillola. Il suo anello non si disegna mentre l'anellino lo porta (`FluidActivityRing.covers`).
      val look = pillLook
      if (look != null && !consumed) {
        val swap = swaps.lastOrNull { it.first <= t }?.second
        val faces = if (swap == null) listOf(FaceRunning to SharpFace) else
          swap.frame(t).layers.map { layer ->
            (layer.key as String) to FaceLook(layer.look.alpha, layer.look.scale,
              glassContentBlurRadiusPx(glassContentSigmaPx(layer.look.blur, BlurReferenceDp)))
          }
        val pulse = timeline.outcomeAt?.let { pulseAt(t - it) } ?: 0f
        bar += pillFace(pillRect, slimRect, look, faces, pulse, ink, "pillola", glyph = minimize <= 0f)
      }
      if (titleAbove) title?.let(bar::add)
      elements += if (minimize > 0f) bar.mapNotNull { retracted(it, minimize) } else bar
      // L'anellino, fuori dal livello che ritira la barra e sopra tutto ciò che sale lì.
      ring.element(t, ink)?.let(elements::add)
      // La card dopo la barra (sopra), e prima di lei la pagina: il popover è parallelo, senza velo.
      card?.let { elements += it.elements(t) }
      // La barra di stato, del sistema: sopra tutto.
      elements += text("ora", "9:41", 26f, 20f, 14f, 600, ink.text, "left")
      GlassTraceFrame(t, elements)
    }
    val head = linkedMapOf<String, Any?>(
      "_" to "F6B.10: ${timeline.note}",
      "background" to mapOf("kind" to "color", "color" to ink.page),
    )
    if (timeline.scrollAt == null) {
      head["page"] = pages.first()
      framePages = null
    } else {
      framePages = pages
    }
    timeline.apple?.let { head["apple"] = it }
    return GlassTrace(name, Phase, Density, if (dark) "dark" else "light", Width, height, frames, 120, head)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La pagina, il titolo, le icone
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Le righe di una posta **sotto la barra, col suo spazio**, come in un'app vera: la lista comincia sotto la riga dei
   * controlli ([ListTop]) e ci passa sotto solo quando scorre ([scrollDp]), dove la scioglie il bordo dello scorrimento.
   */
  private fun page(ink: Ink, largeTitle: Boolean, height: Float, scrollDp: Float = 0f): List<Map<String, Any?>> = buildList {
    fun text(text: String, x: Float, baseline: Float, size: Float, weight: Int, color: String) =
      mapOf("kind" to "text", "text" to text, "x" to round3(x), "y" to round3(baseline), "sizeDp" to size, "weight" to weight,
        "color" to color, "align" to "left")
    var y = (if (largeTitle) 128f else ListTop) - scrollDp
    if (largeTitle) add(text(Title, 16f, 104f, 34f, 700, ink.text))
    val senders = listOf("Anna Bellini" to "Riunione di domani", "Luca" to "Le foto delle vacanze",
      "Marta R." to "Fattura di novembre", "Giorgio" to "Re: il progetto", "Sara" to "Cena venerdì?",
      "Paolo" to "Il preventivo", "Elena" to "Biglietti del treno", "Davide" to "Foto della festa",
      "Chiara" to "Appunti della lezione", "Banca" to "Estratto conto")
    for ((index, sender) in senders.withIndex()) {
      if (y > height - 20f) break
      if (y + RowHeight < 0f) {
        y += RowPitch
        continue
      }
      add(mapOf("kind" to "rect", "x" to 16, "y" to round3(y), "w" to Width - 32f, "h" to RowHeight, "radius" to 14,
        "color" to ink.row))
      add(mapOf("kind" to "circle", "x" to 46, "y" to round3(y + 31f), "r" to 18, "color" to ink.stripe[index % ink.stripe.size]))
      add(text(sender.first, 76f, y + 27f, 15f, 600, ink.text))
      add(text(sender.second, 76f, y + 47f, 13f, 400, ink.secondary))
      y += RowPitch
    }
  }

  /**
   * Il bordo dello scorrimento in alto (F2.5) come lo disegna la barra della schermata: lo shader di produzione
   * (`GlassEdgeBlurShaderString`) con gli uniform di [glassScrollEdgeUniforms], il profilo di serie in alto, la
   * striscia alta quanto barra di stato e riga più la sua portata, il velo nel colore della pagina. Le due passate
   * dell'engine (la sfocatura orizzontale, poi la verticale col velo) sono una catena (`passes`), come
   * `RenderEffect.createChainEffect`.
   */
  private fun scrollEdge(ink: Ink, amount: Float): List<Map<String, Any?>> {
    val profile = fluidScrollEdgeProfile(FluidScrollEdgeSide.Top, FluidScrollEdgeStyle.Automatic)!!.atResolution(1f)
    val controls = BarTop + RowDp
    val unit = FluidScrollEdgeDefaults.ControlHeight.value
    val strip = controls + unit * profile.reach
    val px = RenderDensity
    val veil = Color(("FF" + ink.page.removePrefix("#")).toLong(16))
    val u = glassScrollEdgeUniforms(profile, top = true, widthPx = Width * px, heightPx = strip * px, controlsPx = controls * px,
      unitPx = unit * px, veilColor = veil, darkContent = 0f, amount = amount)
    fun list(values: FloatArray, size: Int) = values.toList().chunked(size).map { chunk -> chunk.map(::round6) }
    val uniforms = linkedMapOf<String, Any?>(
      "axis" to listOf(1, 0),
      "bounds" to listOf(0, 0, "\$captureSize"),
      "centerWeight" to round6(u.centerWeight),
      "taps" to list(u.taps, 2),
      "edgeLine" to u.edgeLine.map(::round6),
      "blurKnots" to list(u.blurKnots, 4),
      "blurShare" to round6(u.blurShare),
      "veilKnots" to list(u.veilKnots, 4),
      "veilKnob" to u.veilKnob.map(::round6),
      "veilColor" to ink.page,
      "shade" to u.shade.map(::round6),
      "fadeRamp" to u.fadeRamp.map(::round6),
      "amount" to round6(u.amount),
      "split" to u.split.map(::round6),
      "grain" to round6(u.grain),
      "finish" to 0,
    )
    return listOf(linkedMapOf(
      "kind" to "shader", "name" to "bordo", "source" to "kotlin:GlassEdgeBlurShaderString",
      "x" to 0, "y" to 0, "w" to Width, "h" to round3(strip), "scale" to 1.0, "inputs" to mapOf("content" to emptyMap<String, Any?>()),
      "uniforms" to uniforms, "passes" to listOf(mapOf("uniforms" to mapOf("axis" to listOf(0, 1), "finish" to 1))),
    ))
  }

  private fun round6(v: Float): Float = (Math.round(v * 1_000_000.0) / 1_000_000.0).toFloat()

  // ---------------------------------------------------------------------------------------------------------------
  // Il ritiro della barra e l'anellino (secondo giro)
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Lo scorrimento della scena e il ritiro che ne segue: il dito porta la lista in su di [ScrollDp] in [ScrollMs]
   * (morbido ai due capi) da [Timeline.scrollAt], e in giù da [Timeline.returnAt]. Il ritiro segue il dito come in
   * `FluidToolbarMinimizeState.scroll`: avanza della corsa fatta sulla corsa della barra ([TravelDp]) e torna indietro
   * appena il dito inverte. La barra si stringe a [FluidToolbarDefaults.RetractingFrom] e torna larga a zero
   * ([fluidToolbarRetractingAfter]), campionata a 120 Hz.
   */
  private inner class Retraction(private val timeline: Timeline) {
    private fun ease(u: Double): Double = u * u * (3.0 - 2.0 * u)

    private fun drag(t: Double, at: Double?): Double {
      if (at == null || t <= at) return 0.0
      return ScrollDp * ease(((t - at) / ScrollMs).coerceIn(0.0, 1.0))
    }

    fun scrollDp(t: Double): Float = (drag(t, timeline.scrollAt) - drag(t, timeline.returnAt)).toFloat()

    fun progress(t: Double): Float {
      val down = drag(t, timeline.scrollAt)
      val up = drag(t, timeline.returnAt)
      return ((down.coerceAtMost(TravelDp.toDouble()) - up) / TravelDp).toFloat().coerceIn(0f, 1f)
    }

    /** Quando la barra si stringe e quando torna larga: i passi di `rememberFluidToolbarRetracting`. */
    val narrowAt: Double?
    val wideAt: Double?

    init {
      var narrow: Double? = null
      var wide: Double? = null
      var retracting = false
      if (timeline.scrollAt != null) {
        for (index in 0..(timeline.lastFrame + 2)) {
          val t = index * 1000.0 / 120.0
          val now = fluidToolbarRetractingAfter(retracting, progress(t))
          if (now && !retracting && narrow == null) narrow = t
          if (!now && retracting && wide == null) wide = t
          retracting = now
        }
      }
      narrowAt = narrow
      wideAt = wide
    }
  }

  /**
   * L'anellino a [t] con le funzioni di produzione: parte dall'anello della pillola compatta, lo segue verso il centro
   * mentre la pillola si stringe (col tempo dello scambio, R7), sale a metà della barra di stato e rimpicciolisce
   * ([fluidActivityRingSpot], [fluidActivityRingHome], [fluidActivityRingAt], [fluidActivityRingScale]).
   */
  private inner class RingJourney(private val retraction: Retraction, compact: Rect, slim: Rect) {
    private val glyph = Offset(compact.left + FluidActivityProfile.SidePadding.value + FluidActivityProfile.Glyph.value / 2f, compact.center.y)
    private val face = slim.center
    private val spot = fluidActivityRingSpot(Width / 2f, BarTop, FluidActivityProfile.RingSmall.value / 2f,
      FluidActivityProfile.RingMargin.value, emptyList())

    /** Il passaggio dall'anello della pillola compatta al centro, come `animateFloatAsState` col tempo di R7. */
    private fun home(t: Double): Float {
      fun ramp(at: Double?): Float = if (at == null || t <= at) 0f else
        FastOutSlowInEasing.transform(((t - at) / GlassContentDefaults.DurationMs).toFloat().coerceIn(0f, 1f))
      val narrow = retraction.narrowAt
      val wide = retraction.wideAt
      return when {
        narrow == null || t < narrow -> 0f
        wide == null || t < wide -> ramp(narrow)
        else -> 1f - ramp(wide)
      }
    }

    fun element(t: Double, ink: Ink): Map<String, Any?>? {
      val progress = retraction.progress(t)
      if (fluidActivityRingAlpha(progress, reduced = false) <= 0f) return null
      val home = fluidActivityRingHome(glyph, face, home(t))!!
      val at = fluidActivityRingAt(home, spot, progress)
      val scale = fluidActivityRingScale(progress, reduced = false)
      val size = FluidActivityProfile.Glyph.value
      val items = ringItems(at, size, scale, ink)
      return mapOf("kind" to "shape", "name" to "anellino", "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "radius" to 0,
        "color" to "#00000000", "content" to listOf(mapOf("name" to "anellino-segno", "x" to 0, "y" to 0, "w" to Width,
          "h" to canvas, "items" to items)))
    }
  }

  /**
   * Un elemento della barra ritirato di [progress] come lo ritira `fluidToolbarMinimizeLayer`: sale della lente, e il suo
   * contenuto svanisce e si sfoca con [fluidToolbarMinimizeLook]. Il vetro (un materiale) svanisce col suo `materialize`
   * e la sua ombra: il banco non sfoca un materiale intero, il dispositivo sì. Svanito, non c'è più (null): a barra
   * ritirata il livello non posa niente.
   */
  @Suppress("UNCHECKED_CAST")
  private fun retracted(element: Map<String, Any?>, progress: Float): Map<String, Any?>? {
    val look = fluidToolbarMinimizeLook(progress, FluidMetrics.GlassButtonLens.value, reducedMotion = false, blurAvailable = true)
    if (look.alpha <= 0.01f) return null
    val blur = glassContentBlurRadiusPx(glassContentSigmaPx(look.blur, FluidMetrics.BarIconSize.value))
    val out = LinkedHashMap(element)
    out["y"] = round3((element["y"] as Number).toFloat() + look.offsetPx)
    (element["content"] as? List<Map<String, Any?>>)?.let { layers ->
      out["content"] = layers.map { layer ->
        val faded = LinkedHashMap(layer)
        faded["alpha"] = round3(((layer["alpha"] as? Number)?.toFloat() ?: 1f) * look.alpha)
        val sharp = (layer["blurDp"] as? Number)?.toFloat() ?: 0f
        if (sharp + blur > 0.05f) {
          faded["blurDp"] = round3(sharp + blur)
          if (faded["marginDp"] == null) faded["marginDp"] = 12
        }
        faded
      }
    }
    (element["body"] as? Map<String, Any?>)?.let { body ->
      val uniforms = LinkedHashMap(body["uniforms"] as Map<String, Any?>)
      uniforms["materialize"] = round3(((uniforms["materialize"] as? Number)?.toFloat() ?: 1f) * look.alpha)
      (uniforms["shadowAlphas"] as? List<Number>)?.let { alphas -> uniforms["shadowAlphas"] = alphas.map { round3(it.toFloat() * look.alpha) } }
      out["body"] = body + ("uniforms" to uniforms)
    }
    return out
  }

  private fun text(name: String, text: String, x: Float, baseline: Float, size: Float, weight: Int, color: String, align: String) =
    mapOf("kind" to "shape", "name" to name, "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "radius" to 0, "color" to "#00000000",
      "content" to listOf(mapOf("name" to "$name-testo", "x" to 0, "y" to 0, "w" to Width, "h" to canvas,
        "items" to listOf(mapOf("kind" to "text", "text" to text, "x" to x, "y" to baseline, "sizeDp" to size,
          "weight" to weight, "color" to color, "align" to align)))))

  private fun barIcons(resolved: FluidToolbarResolved, ink: Ink): Map<String, Any?> {
    val items = ArrayList<Map<String, Any?>>()
    val cy = BarTop + metrics.buttonWidthDp / 2f
    for (element in resolved.elements) {
      if (element.inOverflow || element.item.id == FluidActivityIds.Pill) continue
      val icon = when (element.item.id) {
        "search" -> "fluid:search|search"
        "more" -> "fluid:more_horiz|dots"
        else -> "fluid:arrow_back_ios_new|chevron-left"
      }
      val size = if (icon.startsWith("fluid:arrow")) 25f else 28f
      items += mapOf("kind" to "icon", "icon" to icon, "x" to element.leftDp + element.widthDp / 2f, "y" to cy,
        "sizeDp" to size, "color" to ink.text)
    }
    return mapOf("kind" to "shape", "name" to "icone-barra", "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "radius" to 0,
      "color" to "#00000000", "content" to listOf(mapOf("name" to "icone", "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "items" to items)))
  }

  /** Quanto il titolo ha ceduto il posto a [t]: la rampa lineare di `rememberFluidTitleHandoff`, all'andata e al ritorno. */
  private fun handoff(timeline: Timeline, t: Double): Float {
    val span = GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd
    val present = timeline.appearAt == null
    var value = if (present) 1f else 0f
    timeline.appearAt?.let { at -> if (t >= at) value = ((t - at) / span).toFloat().coerceIn(0f, 1f) }
    timeline.exitAt?.let { at -> if (t >= at) value = 1f - ((t - at) / span).toFloat().coerceIn(0f, 1f) }
    return value
  }

  /**
   * Se il titolo sta sopra i vetri a [t] (`rememberFluidTitleAboveBar`): da quando comincia a cedere il posto a una scomparsa
   * del piano dopo che è tornato.
   */
  private fun titleAboveBar(timeline: Timeline, t: Double): Boolean {
    if (handoff(timeline, t) > 0f) return true
    val back = timeline.exitAt?.let { it + GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd } ?: return false
    return t >= back && t < back + FluidMotion.PlaneDisappearMillis
  }

  /** Il titolo in linea con la curva di `fluidTitleHandoffLayer`: chi esce si sfoca, rimpicciolisce appena e svanisce. */
  private fun titleElement(timeline: Timeline, t: Double, ink: Ink): Map<String, Any?>? {
    val progress = handoff(timeline, t)
    val u = progress * GlassContentDefaults.ExitEnd
    val alpha = if (progress <= 0f) 1f else GlassContentCurves.alpha(GlassContentRole.Exit, u, true)
    if (alpha <= 0.001f) return null
    val scale = if (progress <= 0f) 1f else GlassContentCurves.exitScale.at(u)
    val blur = if (progress <= 0f) 0f else
      glassContentBlurRadiusPx(glassContentSigmaPx(GlassContentCurves.blur(GlassContentRole.Exit, u) * GlassContentDefaults.PeakBlur, 17f))
    val cy = BarTop + metrics.buttonWidthDp / 2f
    val layer = linkedMapOf<String, Any?>(
      "name" to "titolo", "x" to Width / 2f - 60f, "y" to cy - 14f, "w" to 120, "h" to 28,
      "label" to Title, "labelSizeDp" to 17, "labelWeight" to 600, "color" to ink.text,
      "alpha" to round3(alpha), "scale" to round3(scale),
    )
    if (blur > 0.05f) {
      layer["blurDp"] = round3(blur)
      layer["marginDp"] = 12
    }
    return mapOf("kind" to "shape", "name" to "titolo-in-linea", "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "radius" to 0,
      "color" to "#00000000", "content" to listOf(layer))
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La faccia della pillola
  // ---------------------------------------------------------------------------------------------------------------

  /** L'aspetto della lente del piano sul contenuto della pillola (dal suo strato in `F38aTraces`). */
  private data class PillLook(val alpha: Float, val scale: Float, val blurDp: Float, val clip: Map<String, Any?>?) {
    companion object {
      /** Lo strato del piano dentro il suo elemento, che sta in ([ox], [oy]): il ritaglio torna in coordinate della tela. */
      @Suppress("UNCHECKED_CAST")
      fun of(layer: Map<String, Any?>, ox: Float, oy: Float): PillLook = PillLook(
        (layer["alpha"] as? Number)?.toFloat() ?: 1f,
        (layer["scale"] as? Number)?.toFloat() ?: 1f,
        (layer["blurDp"] as? Number)?.toFloat() ?: 0f,
        (layer["clip"] as? Map<String, Any?>)?.let { clip ->
          clip + mapOf(
            "x" to round3((clip.getValue("x") as Number).toFloat() + ox),
            "y" to round3((clip.getValue("y") as Number).toFloat() + oy),
          )
        },
      )
    }
  }

  private data class FaceLook(val alpha: Float, val scale: Float, val blurDp: Float)

  private val SharpFace = FaceLook(1f, 1f, 0f)
  private val FaceRunning = "lavoro"
  private val FaceDone = "esito"
  private val FaceMinimal = "minima"

  /** La sfocatura dello scambio si misura sull'icona della barra, come nel posto (`blurReference`). */
  private val BlurReferenceDp = 28f

  /** Il battito dell'esito a [since] ms dall'esito: dopo `PulseDelayFraction` dello scambio, una campana di `PulseMs`. */
  private fun pulseAt(since: Double): Float {
    val delay = GlassContentDefaults.DurationMs * FluidActivityProfile.PulseDelayFraction
    val u = ((since - delay) / FluidActivityProfile.PulseMs).toFloat()
    return fluidActivityPulseAt(u)
  }

  /**
   * Gli strati della faccia: per ogni faccia in vista ([faces], con l'aspetto dello scambio) il segno, la frase e la
   * percentuale, scalati attorno al centro della pillola come li scala la lente ([look]) e ritagliati sul vetro.
   */
  private fun pillFace(
    rect: Rect,
    slim: Rect,
    look: PillLook,
    faces: List<Pair<String, FaceLook>>,
    pulse: Float,
    ink: Ink,
    name: String,
    glyph: Boolean = true,
  ): Map<String, Any?> {
    val layers = faces.mapNotNull { (face, swap) ->
      val alpha = look.alpha * swap.alpha
      if (alpha <= 0.001f) return@mapNotNull null
      val scale = look.scale * swap.scale
      val items = faceItems(face, if (face == FaceMinimal) slim else rect, scale, ink, pulse, glyph = glyph)
      if (items.isEmpty()) return@mapNotNull null
      val layer = linkedMapOf<String, Any?>(
        "name" to "$name-$face", "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "items" to items, "alpha" to round3(alpha),
      )
      val blur = look.blurDp + swap.blurDp
      if (blur > 0.05f) {
        layer["blurDp"] = round3(blur)
        layer["marginDp"] = 12
      }
      look.clip?.let { layer["clip"] = it }
      layer
    }
    return mapOf("kind" to "shape", "name" to name, "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "radius" to 0,
      "color" to "#00000000", "content" to layers)
  }

  /** Il contenuto di una faccia in coordinate della tela, scalato di [scale] attorno al centro di [rect]. */
  private fun faceItems(
    face: String,
    rect: Rect,
    scale: Float,
    ink: Ink,
    pulse: Float,
    cardScale: Float = 1f,
    glyph: Boolean = true,
  ): List<Map<String, Any?>> {
    val c = rect.center
    fun x(v: Float) = round3(c.x + (v - c.x) * scale)
    fun y(v: Float) = round3(c.y + (v - c.y) * scale)
    fun s(v: Float) = round3(v * scale * cardScale)
    val p = FluidActivityProfile
    val glyphDp = p.Glyph.value
    val cy = rect.center.y
    val out = ArrayList<Map<String, Any?>>()
    if (face == FaceMinimal) {
      // La forma minima: il solo anello, al centro. Non c'è mentre l'anellino lo porta.
      if (glyph) out += ringItems(Offset(x(c.x).toFloat(), y(cy).toFloat()), glyphDp, scale * cardScale, ink)
      return out
    }
    if (face == FaceDone) {
      // L'esito: la spunta e la frase, al centro della larghezza del lavoro; il battito fa crescere la spunta e accende
      // la pellicola dentro il vetro.
      if (pulse > 0f) {
        out += mapOf("kind" to "rect", "x" to x(rect.left + 3f), "y" to y(rect.top + 3f), "w" to s(rect.width - 6f),
          "h" to s(rect.height - 6f), "radius" to s((rect.height - 6f) / 2f),
          "color" to ink.accent + alphaHex(FluidActivityProfile.PulseFilmAlpha * pulse))
      }
      val total = glyphDp + p.Gap.value + PhraseDp
      val left = c.x - total / 2f
      val grow = 1f + (FluidActivityProfile.PulseScale - 1f) * pulse
      out += mapOf("kind" to "icon", "icon" to "fluid:check|check", "x" to x(left + glyphDp / 2f), "y" to y(cy),
        "sizeDp" to s(glyphDp * grow), "color" to ink.text)
      out += mapOf("kind" to "text", "text" to Phrase, "x" to x(left + glyphDp + p.Gap.value), "y" to y(cy + 14f * 0.36f),
        "sizeDp" to s(14f), "weight" to 600, "color" to ink.text, "align" to "left")
      return out
    }
    // Il lavoro: l'anello (la pista, l'arco a punti da ore 12, l'icona dentro), la frase, la percentuale.
    val ringCx = rect.left + p.SidePadding.value + glyphDp / 2f
    if (glyph) out += ringItems(Offset(x(ringCx).toFloat(), y(cy).toFloat()), glyphDp, scale * cardScale, ink)
    val phraseLeft = rect.left + p.SidePadding.value + glyphDp + p.Gap.value
    out += mapOf("kind" to "text", "text" to Phrase, "x" to x(phraseLeft), "y" to y(cy + 14f * 0.36f), "sizeDp" to s(14f),
      "weight" to 600, "color" to ink.text, "align" to "left")
    out += mapOf("kind" to "text", "text" to "${fluidActivityPercent(Progress)}%", "x" to x(rect.right - p.SidePadding.value),
      "y" to y(cy + 14f * 0.36f), "sizeDp" to s(14f), "weight" to 500, "color" to ink.text, "align" to "right")
    return out
  }

  private fun alphaHex(alpha: Float): String = "%02X".format((alpha.coerceIn(0f, 1f) * 255f).toInt())

  /** L'anello del lavoro centrato in [center], del segno [sizeDp] scalato di [scale]: la pista, l'arco a punti, l'icona. */
  private fun ringItems(center: Offset, sizeDp: Float, scale: Float, ink: Ink): List<Map<String, Any?>> {
    val p = FluidActivityProfile
    val glyph = sizeDp * scale
    val stroke = p.RingStroke.value * scale
    val r = (glyph - stroke) / 2f
    val out = ArrayList<Map<String, Any?>>()
    out += mapOf("kind" to "shape", "x" to round3(center.x - glyph / 2f), "y" to round3(center.y - glyph / 2f), "w" to round3(glyph),
      "h" to round3(glyph), "radius" to round3(glyph / 2f), "corner" to "round", "stroke" to round3(stroke),
      "color" to ink.text + alphaHex(FluidActivityProfile.RingTrackAlpha))
    val dots = (Progress * 40).toInt()
    for (i in 0..dots) {
      val a = -PI / 2 + 2 * PI * Progress * i / dots.coerceAtLeast(1)
      out += mapOf("kind" to "circle", "x" to round3(center.x + (r * cos(a)).toFloat()), "y" to round3(center.y + (r * sin(a)).toFloat()),
        "r" to round3(stroke / 2f), "color" to ink.text)
    }
    out += mapOf("kind" to "icon", "icon" to "fluid:sync|refresh", "x" to round3(center.x), "y" to round3(center.y),
      "sizeDp" to round3(glyph * p.RingIconFraction), "color" to ink.text)
    return out
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La card dalla pillola (il popover dalla sorgente di F5B.1)
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Il viaggio della card, coi costruttori di `F5b1ModalTraceTest`: all'apertura ([openAt]) la pillola si raccoglie in
   * goccia e la card si apre attorno a lei col contenuto sfocato e piegato dalla lente; alla chiusura ([closeAt]) rientra.
   * Mentre la card c'è la pillola del piano è ceduta (il passaggio istantaneo del tasto solo, F5A.2/F5B.1).
   */
  private inner class CardJourney(
    private val anchor: Rect,
    private val openAt: Double,
    private val closeAt: Double,
    private val ink: Ink,
    private val heightDp: Float,
    /** Il tema: il vetro della card è quello del suo lato (nel secondo giro restava chiaro anche in scuro). */
    private val dark: Boolean,
  ) {
    private val target = run {
      val w = 300f
      val h = 158f
      val left = (anchor.center.x - w / 2f).coerceIn(16f, Width - 16f - w)
      Rect(left, anchor.bottom + 10f, left + w, anchor.bottom + 10f + h)
    }
    private val motion = FluidModalSourceMotion.Popover
    private val optics = GlassDefaults.panelOptics().sanitized()
    private val dress = LiquidPhysicsDress(GlassRole.Modal, PhysicsMaterialSource(optics, optics), Density, darkSide = dark, shadow = true)
    private var segment: Segment? = null
    private var returned = Double.POSITIVE_INFINITY

    private inner class Segment(val closing: Boolean, val startMs: Double) {
      val start = FluidForm.Slab(anchor, FluidCornerRadii.all(anchor.height / 2f))
      val end = FluidForm.Slab(target, FluidCornerRadii.all(24f))
      val physics = FluidPhysicsState(start)
      val driver = TodayLiquidDriver(physics, Density)
      val from = if (closing) 1f else 0f
      val to = 1f - from
      val travel = TodaySpring(fluidModalGeometrySpec(motion, !closing, from), from, to)
      val focusClock = TodaySpring(fluidModalFocusSpec(motion, !closing, from, fresh = !closing), from, to)
      val visibility = TodaySpring(if (closing) fluidMenuReturnInkSpec(1f) else fluidModalInkExitSpec(1f), 1f, to)
      var p = from

      init {
        val inflation = fluidMenuInflation(target.width, target.height, Width, 6f, 240f, 0.6f, 0.15f)
        physics.driveExternally(
          from = start, to = end, progress = { p }, startFrame = null,
          overshootInflationX = inflation.x, overshootInflationY = inflation.y,
          travelSlab = { if (p < 1f) fluidModalSourceSlab(start, end, p, returning = closing) else null },
        )
      }
    }

    /** Vero se a [t] la pillola del piano è ceduta alla card. */
    fun holdsSource(t: Double): Boolean = t >= openAt && t < returned

    fun elements(t: Double): List<Map<String, Any?>> {
      if (t < openAt || t >= returned) return emptyList()
      val closing = t >= closeAt
      val current = segment?.takeIf { it.closing == closing } ?: Segment(closing, if (closing) closeAt else openAt).also { segment = it }
      val nanos = ((t - current.startMs) * 1_000_000.0).toLong()
      current.p = current.travel.value(nanos)
      current.driver.frame(nanos)
      val physics = current.physics
      val plan = physics.ensurePlan()
      val bounds = physics.contentBoundsNow()
      val p = current.p
      val focus = current.focusClock.value(nanos)
      val face = if (closing) fluidModalReturnFaceAlpha(p, bounds, anchor) else fluidModalFaceAlpha(motion, p)
      val alpha = fluidModalContentAlpha(motion, p, focus, current.visibility.value(nanos))
      if (closing && p <= 0.0005f && face >= 0.999f && nanos > 120_000_000L) {
        // Rientrata: da qui la pillola è di nuovo del piano.
        returned = t
        return emptyList()
      }
      val content = ArrayList<Map<String, Any?>>()
      var contentClip: Map<String, Any?>? = null
      if (face > 0f) {
        // La faccia della pillola dentro la goccia: si spegne mentre la goccia si raccoglie, torna quando ci rientra.
        val shrink = min(bounds.width / anchor.width, bounds.height / anchor.height).coerceIn(0f, 1f)
        val faceRect = Rect(Offset(bounds.center.x - anchor.width / 2f, bounds.center.y - anchor.height / 2f), anchor.size)
        content += linkedMapOf("name" to "sorgente", "x" to 0, "y" to 0, "w" to Width, "h" to canvas,
          "items" to faceItems(FaceRunning, faceRect, shrink, ink, 0f), "alpha" to round3(face))
      } else if (alpha > 0f) {
        val map = fluidModalContentMap(physics, target, p, focus)
        val center = target.center + map.translation
        fun px(local: Float) = round3(center.x + (local - target.width / 2f) * map.scale)
        fun py(local: Float) = round3(center.y + (local - target.height / 2f) * map.scale)
        fun sz(v: Float) = round3(v * map.scale)
        val items = ArrayList<Map<String, Any?>>()
        val glyph = FluidActivityProfile.CardGlyph.value
        items += mapOf("kind" to "shape", "x" to px(16f), "y" to py(18f), "w" to sz(glyph), "h" to sz(glyph), "radius" to sz(glyph / 2f),
          "corner" to "round", "stroke" to sz(2.5f), "color" to ink.text + alphaHex(FluidActivityProfile.RingTrackAlpha))
        val dots = (Progress * 56).toInt()
        val r = (glyph - 2.5f) / 2f
        for (i in 0..dots) {
          val a = -PI / 2 + 2 * PI * Progress * i / dots
          items += mapOf("kind" to "circle", "x" to px(16f + glyph / 2f + (r * cos(a)).toFloat()),
            "y" to py(18f + glyph / 2f + (r * sin(a)).toFloat()), "r" to sz(1.25f), "color" to ink.text)
        }
        items += mapOf("kind" to "icon", "icon" to "fluid:sync|refresh", "x" to px(16f + glyph / 2f), "y" to py(18f + glyph / 2f),
          "sizeDp" to sz(16f), "color" to ink.text)
        items += mapOf("kind" to "text", "text" to Phrase, "x" to px(58f), "y" to py(30f), "sizeDp" to sz(17f), "weight" to 600,
          "color" to ink.text, "align" to "left")
        items += mapOf("kind" to "text", "text" to "128 di 300 messaggi", "x" to px(58f), "y" to py(50f), "sizeDp" to sz(14f),
          "weight" to 400, "color" to ink.secondary, "align" to "left")
        items += mapOf("kind" to "text", "text" to "${fluidActivityPercent(Progress)}%", "x" to px(target.width - 16f), "y" to py(30f),
          "sizeDp" to sz(14f), "weight" to 500, "color" to ink.text, "align" to "right")
        // «Pausa» e «Annulla»: due tasti piccoli, la pellicola tinta e quella d'errore.
        items += mapOf("kind" to "rect", "x" to px(16f), "y" to py(102f), "w" to sz(92f), "h" to sz(32f), "radius" to sz(16f),
          "color" to ink.accent + "2E")
        items += mapOf("kind" to "text", "text" to "Pausa", "x" to px(62f), "y" to py(123f), "sizeDp" to sz(15f), "weight" to 600,
          "color" to ink.accent, "align" to "center")
        items += mapOf("kind" to "rect", "x" to px(116f), "y" to py(102f), "w" to sz(100f), "h" to sz(32f), "radius" to sz(16f),
          "color" to ink.error + "2E")
        items += mapOf("kind" to "text", "text" to "Annulla", "x" to px(166f), "y" to py(123f), "sizeDp" to sz(15f), "weight" to 600,
          "color" to ink.error, "align" to "center")
        val layer = linkedMapOf<String, Any?>(
          "name" to "card", "x" to 0, "y" to 0, "w" to Width, "h" to canvas, "items" to items, "alpha" to round3(alpha),
          "blurDp" to round3(fluidMenuLensBlurDp(focus) * map.scale),
        )
        menuTraceContentLens(plan, Offset.Zero, 1f, focus)?.let { layer["effect"] = it }
        val clip = fluidModalTravelClip(physics, target, p, focus)?.translate(target.topLeft)
        if (clip != null) contentClip = mapOf(
          "x" to round3(clip.left), "y" to round3(clip.top), "w" to round3(clip.width), "h" to round3(clip.height),
          "radius" to listOf(clip.topLeftCornerRadius.x, clip.topRightCornerRadius.x,
            clip.bottomRightCornerRadius.x, clip.bottomLeftCornerRadius.x).map(::round3),
        )
        content += layer
      }
      val panel = dress.element("card-vetro", physics, plan, 1f, Offset.Zero, Width, heightDp, content, timeMs = t - current.startMs)
      return listOf(contentClip?.let { panel + ("contentClip" to listOf(it)) } ?: panel)
    }
  }
}
