package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le tracce di F6B.7 per il banco del movimento (piano F6B, «campo e composer»): `composer-righe`, `composer-allegati`,
 * `composer-tasto-stati` e `composer-alone`, chiare e scure, a 120 Hz.
 *
 * **La geometria è quella di produzione.** La posa del campo viene da [ToolbarSearch.layout]; l'altezza naturale del
 * contenuto da [fluidSearchFieldContentPx] (la regola del layout del campo: margini di 8 dp, righe di `bodyLarge` da
 * 23 dp, al più `maxLines`, più il vassoio); la crescita dalla molla [ToolbarSearchGrowthSpring], che parte due
 * fotogrammi dopo il cambio del testo (il layout misura, la composizione dopo anima); il vetro dalla posa
 * [toolbarSearchGrowthPlace] col bordo basso fermo, e il piano lo segue **senza viaggio** ([F38aTraces.Scene.instantAt],
 * cioè `FluidGlassAnimation.None`). Il contenuto usa le curve di produzione ([GlassContentJourney]) per le facce del
 * tasto a destra, la riga della voce e il vassoio che esce.
 *
 * **Cosa è approssimato.** La tastiera è finta (sale in 285 ms con la curva (0,2, 0, 0, 1), a occhio, come in F6B.6).
 * Le righe di testo sono già andate a capo nella scena. L'anello del lavoro è un anello intero (la resa del movimento
 * ridotto). L'alone di engine-ai-ui (`FluidAssistantFieldHalo`) è rifatto qui con gli stessi numeri del suo profilo,
 * compreso il moto del 22 novembre (il giro di luce, il respiro, il moto che entra in 400 ms ed esce in 600 rallentando):
 * le macchie sono dischi sfocati al posto dei gradienti radiali, l'anello e la scia sono file di punti, e la somma
 * `Plus` del tema scuro è una sovrapposizione. Il banco non impagina Compose.
 */
internal object ComposerTraces {
  const val Phase = "f6b-7"
  private const val Density = 2f
  private const val Width = 400f
  private const val Height = 460f
  private const val ImeHeight = 260f
  private const val ImeGap = 4f
  private const val ImeMillis = 285.0
  private val ImeCurve = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  private const val Line = 23f
  private const val Pad = 8f
  private const val TrayInset = 6f
  /** (F6B.8b) le pillole del vassoio da 40 dp e lo spazio di 4 fra il vassoio e la riga del testo. */
  private const val Pill = 40f
  private const val TrayGap = 4f
  private const val FieldKey = "campo"
  private const val FirstFrame = -3
  private const val Fps = F38aTraces.Fps
  private val Grow = ToolbarSearchGrowthSpring.let { FloatSpringSpec(it.dampingRatio, it.stiffness, 0.01f) }

  /** La faccia del tasto a destra (F6B.7): il microfono, «Invia», «Invia» al lavoro (l'anello), riuscito, lo stop. */
  enum class Face { Voice, Send, Sending, Sent, Stop }

  /** L'umore dell'alone della scena `composer-alone`. */
  enum class Mood { Rest, Focus, Listening, Working }

  /** Uno stato all'istante [at]: le righe già andate a capo, il vassoio, la faccia, la riga della voce. */
  class Step(
    val at: Double,
    val label: String,
    val lines: List<String> = emptyList(),
    val tray: List<String> = emptyList(),
    val face: Face = Face.Voice,
    val voice: String? = null,
    val mood: Mood = Mood.Focus,
  )

  class Scene(
    val name: String,
    val note: String,
    val dark: Boolean,
    val steps: List<Step>,
    val lastFrame: Int,
    val prompt: String,
    val imeShowAt: Double? = null,
    val imeHideAt: Double? = null,
    val halo: Boolean = false,
    val maxLines: Int = 5,
    val leading: String = "add",
  )

  // --- Le scene -------------------------------------------------------------------------------------------------------

  fun scenes(): List<Scene> = listOf(false, true).flatMap { dark ->
    val s = if (dark) "-scuro" else ""
    val l1 = "Arrivo tra dieci minuti,"
    val l2 = "ho trovato traffico sul"
    val l3 = "raccordo ma sto uscendo"
    val l4 = "adesso. Tu sei già dentro"
    val l5 = "o mi aspetti all'ingresso"
    val l6 = "del cinema? Prendo i popcorn"
    listOf(
      Scene("composer-righe$s", "F6B.7: il campo «Messaggio» cresce di riga in riga, poi scorre dentro; inviato torna a una riga", dark,
        listOf(
          Step(-100.0, "vuoto", face = Face.Voice),
          Step(0.0, "una riga", listOf(l1), face = Face.Send),
          Step(400.0, "due righe", listOf(l1, l2), face = Face.Send),
          Step(800.0, "tre righe", listOf(l1, l2, l3), face = Face.Send),
          Step(1200.0, "quattro", listOf(l1, l2, l3, l4), face = Face.Send),
          Step(1600.0, "cinque, il massimo", listOf(l1, l2, l3, l4, l5), face = Face.Send),
          Step(2000.0, "sei: scorre dentro", listOf(l1, l2, l3, l4, l5, l6), face = Face.Send),
          Step(2400.0, "inviato", face = Face.Voice),
        ), lastFrame = 360, prompt = "Messaggio"),
      Scene("composer-allegati$s", "F6B.7: un allegato apre il vassoio verso l'alto; tolto l'ultimo, si richiude col bordo basso fermo", dark,
        listOf(
          Step(-100.0, "una riga", listOf("Ecco le foto di ieri"), face = Face.Send),
          Step(0.0, "una foto", listOf("Ecco le foto di ieri"), tray = listOf("IMG_2041.jpg"), face = Face.Send),
          Step(500.0, "due foto", listOf("Ecco le foto di ieri"), tray = listOf("IMG_2041.jpg", "IMG_2042.jpg"), face = Face.Send),
          Step(1000.0, "tolta la prima", listOf("Ecco le foto di ieri"), tray = listOf("IMG_2042.jpg"), face = Face.Send),
          Step(1500.0, "tolta l'ultima", listOf("Ecco le foto di ieri"), face = Face.Send),
        ), lastFrame = 276, prompt = "Messaggio"),
      Scene("composer-tasto-stati$s", "F6B.7: il tasto a destra, una sagoma: microfono, «Invia», l'anello, la spunta, lo stop della voce", dark,
        listOf(
          Step(-100.0, "microfono", face = Face.Voice),
          Step(0.0, "scrive: Invia", listOf("Ci vediamo alle 8"), face = Face.Send),
          Step(500.0, "invia: l'anello", listOf("Ci vediamo alle 8"), face = Face.Sending),
          Step(1100.0, "inviato: la spunta", face = Face.Sent),
          Step(1700.0, "di nuovo il microfono", face = Face.Voice),
          Step(2100.0, "detta: la riga della voce, lo stop", face = Face.Stop, voice = "Ti ascolto…"),
          Step(2600.0, "le parole arrivano", face = Face.Stop, voice = "Domani alle nove"),
          Step(3100.0, "smette: il testo", listOf("Domani alle nove"), face = Face.Send),
        ), lastFrame = 420, prompt = "Messaggio"),
      Scene("composer-alone$s", "F6B.7: il composer dell'assistente con l'alone animato: fermo a riposo; aperto il giro di luce e il respiro; la voce; il lavoro", dark,
        listOf(
          Step(-100.0, "a riposo", face = Face.Voice, mood = Mood.Rest),
          Step(300.0, "aperto", face = Face.Voice, mood = Mood.Focus),
          Step(1300.0, "scrive", listOf("Che tempo fa domani"), face = Face.Send, mood = Mood.Focus),
          Step(2100.0, "ascolta", face = Face.Stop, voice = "Ti ascolto…", mood = Mood.Listening),
          Step(3300.0, "lavora", listOf("Che tempo fa domani a Roma?"), face = Face.Stop, mood = Mood.Working),
          Step(4500.0, "chiuso", face = Face.Voice, mood = Mood.Rest),
        ), lastFrame = 660, prompt = "Chiedi all'assistente", imeShowAt = 300.0, imeHideAt = 4500.0, halo = true,
        maxLines = 6, leading = "add"),
    )
  }

  // --- La geometria ----------------------------------------------------------------------------------------------------

  private val Metrics = fluidBottomBarMetrics()
  private val RowHeight = Metrics.buttonWidthDp
  private val FieldHeight = FluidSearchFieldDefaults.BarFieldHeight.value
  private val Pose: ToolbarSearchPose = ToolbarSearch(
    FluidSearchSpec.message("", {}, onSend = {}, active = true, onActiveChange = {}, composer = FluidComposerSlots()),
    FluidToolbarBar.Bottom, true,
  ).layout(FluidToolbarModel(emptyList()), Width, Metrics)

  private fun ime(scene: Scene, t: Double): Float {
    fun rise(from: Double) = ImeCurve.transform(((t - from) / ImeMillis).toFloat().coerceIn(0f, 1f))
    val show = scene.imeShowAt
    val up = if (show == null) 1f else if (t < show) 0f else rise(show)
    val hide = scene.imeHideAt
    val down = if (hide == null || t < hide) 0f else rise(hide)
    return ImeHeight * up * (1f - down)
  }

  /** Il bordo basso del vetro: la riga sopra la tastiera (o in fondo), il vetro di 40 dp al suo centro. */
  private fun fieldBottom(scene: Scene, t: Double): Float {
    val rest = Height - FluidFloatingTabBarDefaults.BottomMargin.value - RowHeight
    val rowTop = minOf(rest, Height - ime(scene, t) - ImeGap - RowHeight)
    return rowTop + (RowHeight + FieldHeight) / 2f
  }

  private fun px(dp: Float) = (dp * Density).roundToInt()

  /** L'altezza in più che il contenuto di [step] chiede, in dp: la regola del layout del campo. */
  private fun target(scene: Scene, step: Step, withTray: Boolean): Float {
    val lines = if (step.voice != null) 1 else maxOf(1, step.lines.size)
    val tray = if (withTray && step.tray.isNotEmpty()) px(TrayInset + Pill + TrayGap) else 0
    val content = fluidSearchFieldContentPx(lines, scene.maxLines, px(Line), px(FieldHeight), px(Pad), tray)
    return fluidSearchFieldExtraPx(content, px(FieldHeight), compact = false) / Density
  }

  /** La crescita nel tempo: la molla verso il bersaglio, ripartita dal valore e dalla velocità di adesso a ogni cambio. */
  private class Growth(scene: Scene) {
    private val frameMs = 1000.0 / Fps
    private val changes = ArrayList<Pair<Double, Float>>()

    init {
      var last = Float.NaN
      for ((i, step) in scene.steps.withIndex()) {
        val before = scene.steps.getOrNull(i - 1)
        // Il vassoio tolto esce sfocato prima (FluidGlassContentTransition lo tiene nel layout finché esce), poi il vetro cala.
        val trayLeaves = before != null && before.tray.isNotEmpty() && step.tray.isEmpty()
        val exitMs = if (trayLeaves) GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd else 0f
        val value = target(scene, step, withTray = true)
        if (value != last) changes += (if (i == 0) Double.NEGATIVE_INFINITY else step.at + exitMs + 2 * frameMs) to value
        last = value
      }
    }

    private var start = 0.0
    private var from = Float.NaN
    private var to = 0f
    private var v0 = 0f
    private var next = 0
    private val moves = ArrayList<Pair<Double, Double>>()

    private fun nanos(t: Double) = ((t - start) * 1_000_000.0).toLong().coerceAtLeast(0L)

    fun at(t: Double): Float {
      while (next < changes.size && changes[next].first <= t + 1e-6) {
        val (at, target) = changes[next++]
        if (from.isNaN()) {
          from = target; to = target; v0 = 0f; start = t
          continue
        }
        val value = Grow.getValueFromNanos(nanos(at), from, to, v0)
        val velocity = Grow.getVelocityFromNanos(nanos(at), from, to, v0)
        from = value; to = target; v0 = velocity; start = at
        moves += at to at + Grow.getDurationNanos(from, to, v0) / 1_000_000.0 + 2 * frameMs
      }
      return Grow.getValueFromNanos(nanos(t), from, to, v0)
    }

    /** Vero mentre il vetro segue la molla (più due fotogrammi): il piano senza viaggio. */
    fun following(t: Double): Boolean = moves.any { (a, b) -> t >= a && t <= b }
  }

  private fun glass(scene: Scene, t: Double, extra: Float): Rect {
    val bottom = fieldBottom(scene, t)
    val place = toolbarSearchGrowthPlace(px(FieldHeight), px(extra), upward = true)
    val top = bottom - FieldHeight + place.top / Density
    return Rect(Pose.left, top, Pose.left + Pose.width, top + place.height / Density)
  }

  private fun plane(rect: Rect): GlassPlaneState {
    val bounds = Rect(rect.left * Density, rect.top * Density, rect.right * Density, rect.bottom * Density)
    val radius = minOf(FluidTextFieldDefaults.MultilineRadius.value * Density, bounds.height / 2f)
    return GlassPlaneState(listOf(GlassPlanePiece(FieldKey, bounds, FluidCornerRadii.all(radius), id = FieldKey)),
      FluidGlassContainerDefaults.Spacing.value * Density, Rect(0f, 0f, Width * Density, Height * Density))
  }

  // --- Il contenuto -----------------------------------------------------------------------------------------------------

  private fun ink(dark: Boolean) = if (dark) "#F2F2F7" else "#17171A"
  private fun accent(dark: Boolean) = if (dark) "#0A84FF" else "#007AFF"
  private fun hex(alpha: Float) = (alpha.coerceIn(0f, 1f) * 255).roundToInt().toString(16).padStart(2, '0').uppercase()

  private fun blurDp(fraction: Float, sizeDp: Float) = glassContentBlurRadiusPx(glassContentSigmaPx(fraction, sizeDp * Density)) / Density

  private fun icon(name: String, x: Float, y: Float, color: String, size: Float = FluidSearchFieldDefaults.IconSize.value) =
    linkedMapOf<String, Any?>("kind" to "icon", "icon" to "fluid:$name", "x" to x, "y" to y, "sizeDp" to size, "color" to color)

  private fun text(value: String, x: Float, y: Float, color: String, size: Float = 16f) =
    linkedMapOf<String, Any?>("kind" to "text", "text" to value, "x" to x, "y" to y, "sizeDp" to size, "color" to color)

  private fun layer(
    name: String, items: List<Map<String, Any?>>, clip: Rect?, alpha: Float = 1f, scale: Float = 1f, blur: Float = 0f,
    box: Rect = clip ?: Rect(0f, 0f, Width, Height), radius: Float = FluidTextFieldDefaults.MultilineRadius.value,
  ): Map<String, Any?>? {
    if (alpha <= 0.001f || items.isEmpty()) return null
    // Lo strato è il riquadro [box] (la scala gira attorno al suo centro, come un `graphicsLayer`); le voci, scritte in
    // coordinate della scena, si spostano nel riquadro; il ritaglio resta in coordinate della scena (l'elemento è a 0, 0).
    val moved = items.map { item ->
      LinkedHashMap(item).also { it["x"] = (it.getValue("x") as Number).toFloat() - box.left; it["y"] = (it.getValue("y") as Number).toFloat() - box.top }
    }
    val out = linkedMapOf<String, Any?>("name" to name, "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height,
      "items" to moved, "alpha" to alpha.coerceIn(0f, 1f), "scale" to scale, "blurDp" to blur, "marginDp" to 12)
    if (clip != null) out["clip"] = linkedMapOf("x" to clip.left, "y" to clip.top, "w" to clip.width, "h" to clip.height,
      "radius" to minOf(radius, clip.height / 2f, clip.width / 2f))
    return out
  }

  /** Il tasto a destra con la faccia [face], centrato in ([cx], [cy]). */
  private fun faceItems(face: Face, cx: Float, cy: Float, dark: Boolean): List<Map<String, Any?>> {
    val color = ink(dark)
    // F6B.8b: il disco di «Invia» da 40 dp nel posto da 44, la freccia è da 22; le icone da 26.
    val glyph = FluidSearchFieldDefaults.CommandIconSize.value
    fun disc() = linkedMapOf<String, Any?>("kind" to "rect", "x" to cx - 20f, "y" to cy - 20f, "w" to 40f, "h" to 40f,
      "radius" to "capsule", "color" to accent(dark))
    return when (face) {
      Face.Voice -> listOf(icon("mic", cx, cy, color, glyph))
      Face.Stop -> listOf(icon("stop.fill|stop", cx, cy, color, glyph))
      Face.Send -> listOf(disc(), icon("arrow_upward", cx, cy, "#FFFFFF", 22f))
      Face.Sending -> listOf(disc(),
        linkedMapOf("kind" to "shape", "x" to cx - 11f, "y" to cy - 11f, "w" to 22f, "h" to 22f, "radius" to 11f,
          "stroke" to 1.6f, "color" to "#FFFFFFE6"),
        icon("arrow_upward", cx, cy, "#FFFFFF", 12f))
      Face.Sent -> listOf(disc(), icon("check", cx, cy, "#FFFFFF", 22f))
    }
  }

  // --- L'alone (FluidAssistantFieldHalo, coi numeri del suo profilo) -------------------------------------------------------

  /** I colori dell'alone del banco: accento, secondario e terziario a occhio, come un tema di Material. */
  private fun haloColours(dark: Boolean) = if (dark) listOf("#5B9BFF", "#B98CFF", "#2DD4BF") else listOf("#3D7BFF", "#9B5CF6", "#14B8A6")

  /** La presenza a riposo di `FluidAssistantFieldHaloProfile.RestPresence` (engine-ai-ui, che questo modulo non vede). */
  private const val RestPresence = 0.70f

  private class Halo(val scene: Scene) {
    var time = 0f
    var amplitude = 0f
    var presence = RestPresence
    /** Il moto (F6B.7, Alessio, 22 novembre): 0 fermo, 1 pieno; entra in 400 ms ed esce in 600. */
    var motion = 0f
    private var last = Double.NaN

    /** Il respiro: l'alfa va su e giù del 12% in 4,5 s di tempo dell'orologio, quanto vale il moto. */
    val breath: Float get() = 1f + 0.12f * motion * sin(2f * PI.toFloat() * time / 4.5f)

    fun step(t: Double, mood: Mood) {
      val dt = if (last.isNaN()) 0f else ((t - last) / 1000.0).toFloat()
      last = t
      // Il microfono finto: una voce che sale e scende in sillabe.
      val level = (0.5f + 0.5f * sin((t / 1000.0 * 2 * PI * 2.3).toFloat())) * (0.6f + 0.4f * sin((t / 1000.0 * 2 * PI * 0.7).toFloat()))
      val target = when (mood) {
        Mood.Rest -> 0f
        Mood.Focus -> 0.35f
        Mood.Listening -> 0.30f + 0.70f * level.coerceIn(0f, 1f)
        Mood.Working -> 0.6f
      }
      // I passi di FluidAssistantFieldHaloProfile: aperto 1, in ascolto 1,2, al lavoro 1,6.
      val tempo = when (mood) { Mood.Rest, Mood.Focus -> 1f; Mood.Listening -> 1.2f; Mood.Working -> 1.6f }
      // Il moto in dissolvenza (in linea al posto del tween del composer), e il tempo che rallenta con lui.
      val moving = mood != Mood.Rest
      motion = if (moving) minOf(1f, motion + dt * 1000f / 400f) else maxOf(0f, motion - dt * 1000f / 600f)
      time += dt * tempo * motion
      val per = if (target > amplitude) 0.35f else 0.08f
      amplitude += (target - amplitude) * (1f - (1f - per).pow(dt * 60f))
      // La presenza: dal 70% a riposo al pieno in 220 ms, e indietro in 260 (in linea).
      val presenceTarget = if (mood == Mood.Rest) RestPresence else 1f
      val fadeMs = if (presenceTarget > presence) 220f else 260f
      val stepBy = (1f - RestPresence) * dt * 1000f / fadeMs
      presence = if (presenceTarget > presence) minOf(presenceTarget, presence + stepBy) else maxOf(presenceTarget, presence - stepBy)
    }

    /** Il punto del bordo arrotondato [rect] (raggio [r]) alla frazione [f] del perimetro, in senso orario dall'alto a sinistra. */
    fun along(rect: Rect, r: Float, f: Float): Pair<Float, Float> {
      val w = rect.width - 2 * r
      val h = rect.height - 2 * r
      val arc = (PI / 2 * r).toFloat()
      val total = 2 * w + 2 * h + 4 * arc
      var d = (((f % 1f) + 1f) % 1f) * total
      fun corner(cx: Float, cy: Float, start: Double): Pair<Float, Float> {
        val a = start + d / r
        return (cx + r * kotlin.math.cos(a)).toFloat() to (cy + r * kotlin.math.sin(a)).toFloat()
      }
      if (d < w) return rect.left + r + d to rect.top
      d -= w
      if (d < arc) return corner(rect.right - r, rect.top + r, -PI / 2)
      d -= arc
      if (d < h) return rect.right to rect.top + r + d
      d -= h
      if (d < arc) return corner(rect.right - r, rect.bottom - r, 0.0)
      d -= arc
      if (d < w) return rect.right - r - d to rect.bottom
      d -= w
      if (d < arc) return corner(rect.left + r, rect.bottom - r, PI / 2)
      d -= arc
      if (d < h) return rect.left to rect.bottom - r - d
      d -= h
      return corner(rect.left + r, rect.top + r, PI)
    }

    fun mix(colours: List<String>, f: Float): String {
      val n = colours.size
      val p = (((f % 1f) + 1f) % 1f) * n
      val i = p.toInt() % n
      val k = p - p.toInt()
      fun ch(c: String, j: Int) = c.substring(1 + 2 * j, 3 + 2 * j).toInt(16)
      val a = colours[i]
      val b = colours[(i + 1) % n]
      return "#" + (0..2).joinToString("") { j -> (ch(a, j) + (ch(b, j) - ch(a, j)) * k).roundToInt().toString(16).padStart(2, '0') }.uppercase()
    }

    /** Le macchie dietro al vetro: dischi sfocati sul bordo (sei da 26 dp, 0,18–0,45 di alfa per la presenza). */
    fun behind(rect: Rect, dark: Boolean): List<Map<String, Any?>> {
      val colours = haloColours(dark)
      val cycle = listOf(colours[0], colours[1], colours[2], colours[0], colours[1], colours[2])
      val n = 6
      val alpha = (0.18f + (0.45f - 0.18f) * amplitude) * presence * breath
      val r = minOf(20f, rect.height / 2f)
      return (0 until n).mapNotNull { i ->
        val f = i.toFloat() / n + time * 0.06f + 0.012f * sin(time * 0.9f + i * 1.7f)
        val (x, y) = along(rect, r, f)
        val breath = 0.85f + 0.15f * sin(time * 1.1f + i * 2f * PI.toFloat() / n)
        val radius = 26f * (0.75f + 0.45f * amplitude) * breath
        val weight = if (i >= 3) 0.88f else 1f
        val disc = linkedMapOf<String, Any?>("kind" to "circle", "x" to x, "y" to y, "r" to radius * 0.55f,
          "color" to cycle[i] + hex(alpha * weight))
        layerOf("macchia-$i", listOf(disc), blur = radius * 0.45f)
      }
    }

    /** L'anello sopra il bordo: punti colorati dal ciclo che gira in 8 s, alfa 0,38 + 0,40 × ampiezza, per la presenza (2 dp). */
    fun above(rect: Rect, dark: Boolean): List<Map<String, Any?>> {
      val colours = haloColours(dark)
      val alpha = ((0.38f + 0.40f * amplitude.coerceIn(0f, 1f)) * presence * breath).coerceIn(0f, 1f)
      val r = minOf(20f, rect.height / 2f)
      val perimeter = 2 * (rect.width - 2 * r) + 2 * (rect.height - 2 * r) + 2 * PI.toFloat() * r
      val count = (perimeter / 2.5f).roundToInt()
      val phase = ((time * 0.125f) % 1f + 1f) % 1f
      val cx = rect.center.x
      val cy = rect.center.y
      val dots = (0 until count).map { k ->
        val (x, y) = along(rect, r, k.toFloat() / count)
        val angle = ((atan2(y - cy, x - cx) / (2 * PI)).toFloat() + 1f) % 1f
        linkedMapOf<String, Any?>("kind" to "circle", "x" to x, "y" to y, "r" to 1.2f, "color" to mix(colours, angle - phase))
      }
      return listOfNotNull(layerOf("anello", dots, alpha = alpha, blur = 0.6f)) + comet(rect, r, perimeter, phase, colours)
    }

    /**
     * Il giro di luce: la scia di 0,16 del bordo, la testa che gira in 5 s di tempo dell'orologio, accesa dalla coda alla
     * testa col quadrato; un filo da 2,5 dp all'alfa 0,65 dentro un bagliore da 7 dp all'alfa 0,22, del colore dell'anello
     * sotto la testa. Sul banco sono punti fitti (il banco non ha tracciati con lo spessore lungo un bordo arrotondato).
     */
    private fun comet(rect: Rect, r: Float, perimeter: Float, phase: Float, colours: List<String>): List<Map<String, Any?>> {
      val strength = presence * motion * breath
      if (strength <= 0.002f) return emptyList()
      val head = ((time * 0.2f) % 1f + 1f) % 1f
      val span = 0.16f
      val (hx, hy) = along(rect, r, head)
      val angle = ((atan2(hy - rect.center.y, hx - rect.center.x) / (2 * PI)).toFloat() + 1f) % 1f
      val colour = mix(colours, angle - phase)
      val count = (span * perimeter / 1.2f).roundToInt()
      val core = ArrayList<Map<String, Any?>>()
      val glow = ArrayList<Map<String, Any?>>()
      for (k in 0 until count) {
        val u = (k + 1).toFloat() / count
        val w = u * u * strength
        val (x, y) = along(rect, r, head - span + span * u)
        core += linkedMapOf("kind" to "circle", "x" to x, "y" to y, "r" to 1.25f, "color" to colour + hex(0.65f * w))
        glow += linkedMapOf("kind" to "circle", "x" to x, "y" to y, "r" to 3.5f, "color" to colour + hex(0.22f * w))
      }
      return listOfNotNull(layerOf("scia-bagliore", glow, blur = 1.6f), layerOf("scia", core, blur = 0.4f))
    }

    private fun layerOf(name: String, items: List<Map<String, Any?>>, alpha: Float = 1f, blur: Float = 0f): Map<String, Any?>? =
      if (alpha <= 0.002f) null else linkedMapOf("name" to name, "x" to 0f, "y" to 0f, "w" to Width, "h" to Height,
        "items" to items, "alpha" to alpha.coerceIn(0f, 1f), "scale" to 1f, "blurDp" to blur, "marginDp" to 40)
  }

  // --- La traccia ------------------------------------------------------------------------------------------------------

  fun build(scene: Scene): GlassTrace {
    val growth = Growth(scene)
    val frames = (FirstFrame..scene.lastFrame).map { glassTraceTimeMs(it, Fps) }
    val rects = frames.map { t -> glass(scene, t, growth.at(t)) }
    val following = frames.map { growth.following(it) }
    val background: Map<String, Any?> = linkedMapOf("kind" to "synthetic", "scaleDp" to 64, "detail" to .45,
      "palette" to if (scene.dark) listOf("#16283C", "#5A3145", "#1F423E") else listOf("#E6D0A0", "#AFC9D7", "#F7EEDD"))
    val base = F38aTraces.build(F38aTraces.Scene(scene.name, scene.note, Width, Height, scene.dark,
      plane(rects.first()), frames.drop(1).zip(rects.drop(1)).map { (t, r) -> F38aTraces.Step(t, plane(r)) }, emptyMap(),
      scene.lastFrame, density = Density, firstFrame = FirstFrame, phase = Phase, background = background,
      motion = GlassPlaneMotion(spec = ToolbarSearchTiming), instantAt = { true }))
    val stepAt = { t: Double -> scene.steps.last { it.at <= t + 1e-6 } }
    val first = scene.steps.first()
    var face = GlassContentJourney.rest(first.face)
    var swap = GlassContentJourney.rest(if (first.voice != null) "voce" else "testo")
    var tray = GlassContentJourney.rest(first.tray.takeIf { it.isNotEmpty() }?.let { "vassoio" })
    var trayShown = first.tray
    var previous: Step = first
    var lastVoice = first.voice ?: "Ti ascolto…"
    val halo = if (scene.halo) Halo(scene) else null
    val color = ink(scene.dark)
    val out = base.frames.mapIndexed { index, frame ->
      val t = frame.tMs
      val step = stepAt(t)
      if (step !== previous) {
        face = face.retarget(t, step.face)
        swap = swap.retarget(t, if (step.voice != null) "voce" else "testo")
        tray = tray.retarget(t, step.tray.takeIf { it.isNotEmpty() }?.let { "vassoio" })
        if (step.tray.isNotEmpty()) trayShown = step.tray
        if (step.voice != null) lastVoice = step.voice
        previous = step
      }
      val rect = rects[index]
      val bottom = rect.bottom
      val elements = ArrayList<Map<String, Any?>>()
      halo?.step(t, step.mood)
      if (halo != null) elements += linkedMapOf("kind" to "shape", "name" to "alone-dietro", "x" to 0, "y" to 0, "w" to Width,
        "h" to Height, "radius" to 0, "color" to "#00000000", "contentClip" to "none", "content" to halo.behind(rect, scene.dark))
      elements += frame.elements
      val layers = ArrayList<Map<String, Any?>?>()
      val cy = bottom - FieldHeight / 2f
      // Lo slot a sinistra, in fondo accanto all'ultima riga.
      layers += layer("sinistra", listOf(icon(scene.leading, rect.left + 23f, cy, color, FluidSearchFieldDefaults.CommandIconSize.value)), rect,
        box = Rect(rect.left, cy - 22f, rect.left + 46f, cy + 22f))
      // Le righe, ancorate al bordo basso: oltre `maxLines` si vedono le ultime (il cursore in fondo).
      val shown = step.lines.takeLast(scene.maxLines)
      val textX = rect.left + 46f
      val lineItems = shown.mapIndexed { i, value ->
        val center = bottom - Pad - Line * (shown.size - i) + Line / 2f
        text(value, textX, center + 5.5f, color)
      }.ifEmpty { listOf(text(scene.prompt, textX, cy + 5.5f, color + "8C")) }
      for (ink in swap.frame(t).layers) {
        val look = ink.look
        val items = if (ink.key == "voce") {
          val words = step.voice ?: lastVoice
          listOf(linkedMapOf<String, Any?>("kind" to "circle", "x" to textX + 8f, "y" to cy, "r" to 4.5f, "color" to accent(scene.dark)),
            text(words, textX + 22f, cy + 5.5f, color))
        } else lineItems
        layers += layer("${ink.key}-${ink.id}", items, rect, look.alpha, look.scale, blurDp(look.blur, GlassContentDefaults.BlurReference.value),
          box = Rect(textX, rect.top, rect.right - 44f, rect.bottom))
      }
      // Il vassoio, sopra il testo: esce sfocato quando l'app lo toglie.
      // Il vassoio (6 dp di margine sopra, la pillola da 30) sta sopra le righe: dal bordo basso, la riga e il testo in più.
      val trayTop = bottom - FieldHeight - target(scene, step, withTray = false) - TrayGap - Pill
      for (ink in tray.frame(t).layers) {
        if (ink.key == null) continue
        val look = ink.look
        val items = ArrayList<Map<String, Any?>>()
        var x = rect.left + TrayInset
        for (name in trayShown) {
          // F6B.8b: la pillola da 40 dp, col simbolo e la X da 18 e la scritta `labelLarge`.
          val w = 12f + 18f + 6f + name.length * 7.8f + 6f + 18f + 10f
          items += linkedMapOf("kind" to "rect", "x" to x, "y" to trayTop, "w" to w, "h" to Pill, "radius" to "capsule",
            "color" to color + "17")
          items += icon("image", x + 21f, trayTop + Pill / 2f, color, 18f)
          items += text(name, x + 36f, trayTop + Pill / 2f + 5f, color, 14f)
          items += icon("close", x + w - 19f, trayTop + Pill / 2f, color + "B3", 18f)
          x += w + 6f
        }
        layers += layer("vassoio-${ink.id}", items, rect, look.alpha, look.scale, blurDp(look.blur, GlassContentDefaults.BlurReference.value))
      }
      // Il tasto a destra: una sagoma, le facce si scambiano sfocate.
      for (ink in face.frame(t).layers) {
        val shownFace = ink.key as? Face ?: continue
        val look = ink.look
        val inset = ToolbarSearchTrailingInset.value
        layers += layer("destra-${ink.id}", faceItems(shownFace, rect.right - inset - 22f, cy, scene.dark), rect, look.alpha, look.scale,
          blurDp(look.blur, GlassContentDefaults.BlurReference.value), box = Rect(rect.right - inset - 44f, cy - 22f, rect.right - inset, cy + 22f))
      }
      elements += linkedMapOf("kind" to "shape", "name" to "contenuto", "x" to 0, "y" to 0, "w" to Width, "h" to Height,
        "radius" to 0, "color" to "#00000000", "contentClip" to "none", "content" to layers.filterNotNull())
      if (halo != null) elements += linkedMapOf("kind" to "shape", "name" to "alone-sopra", "x" to 0, "y" to 0, "w" to Width,
        "h" to Height, "radius" to 0, "color" to "#00000000", "contentClip" to "none", "content" to halo.above(rect, scene.dark))
      val imeNow = ime(scene, t)
      if (imeNow > 0.01f) elements += linkedMapOf("kind" to "shape", "name" to "tastiera", "x" to 0, "y" to Height - imeNow,
        "w" to Width, "h" to ImeHeight, "radius" to 0, "color" to if (scene.dark) "#2C2C2E" else "#D1D4DA", "contentClip" to "none",
        "content" to listOf(linkedMapOf("name" to "scritta", "x" to 0, "y" to 0, "w" to Width, "h" to 40,
          "items" to listOf(text("tastiera finta", Width / 2f - 44f, 26f, if (scene.dark) "#8E8E93" else "#6E6E73", 14f)))))
      elements += linkedMapOf("kind" to "shape", "name" to "misure", "x" to 0, "y" to 0, "w" to 1, "h" to 1,
        "color" to "#00000000", "radius" to 0,
        "campoAlto" to rect.top, "campoBasso" to rect.bottom, "campoAltezza" to rect.height, "tastieraAlto" to Height - imeNow,
        "righe" to minOf(maxOf(1, step.lines.size), scene.maxLines), "segue" to if (following[index]) 1 else 0,
        "alonePresenza" to (halo?.presence ?: 0f), "aloneAmpiezza" to (halo?.amplitude ?: 0f), "aloneMoto" to (halo?.motion ?: 0f),
        "aloneTempo" to (halo?.time ?: 0f))
      GlassTraceFrame(t, elements)
    }
    val head = LinkedHashMap(base.head)
    head["_provenienza"] = "FluidSearchSpec.message/assistant -> ToolbarSearch.layout + fluidSearchFieldContentPx + " +
      "ToolbarSearchGrowthSpring + toolbarSearchGrowthPlace -> F38aTraces (piano di produzione, senza viaggio: " +
      "FluidGlassAnimation.None) + GlassContentJourney per facce, voce e vassoio; tastiera finta. Nessun Compose/GPU."
    head["events"] = scene.steps.drop(1).map { linkedMapOf("t" to it.at, "kind" to "mark", "label" to it.label) }
    head["probes"] = listOf("campoAlto", "campoBasso", "campoAltezza", "tastieraAlto", "alonePresenza", "aloneAmpiezza", "aloneMoto").map {
      linkedMapOf("name" to it, "kind" to "value", "path" to "misure.$it")
    }
    return GlassTrace(scene.name, Phase, Density, base.theme, Width, Height, out, Fps, head)
  }
}
