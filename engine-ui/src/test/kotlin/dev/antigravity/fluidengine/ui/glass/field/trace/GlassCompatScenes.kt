package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.glassTintLiquidMode
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import java.io.File
import kotlin.math.max

/*
 * Le scene della TAVOLA DI COMPATIBILITA' (F1A.8; piano, F1A «Rischi e ripieghi»): le ottiche e le tinte scritte a
 * mano che esistono davvero (nell'orologio, nel Playground, nell'engine, e nelle app di Alessio lette in sola
 * lettura a novembre 2026), rese col vetro di oggi e col materiale nuovo, **senza che chi le usa cambi una riga**.
 *
 * Il materiale nuovo e' quello che l'engine disegnerebbe: ogni lastra chiede a `glassRecipe` con la sua ottica
 * (gia' passata da `sanitized()`, come fa l'innesto) e con la tinta **com'e' costruita da chi la usa**; se quella
 * tinta diventa velo, pellicola dipinta o tinta di Apple lo decide la regola dei modi (`glassTintLiquidMode`), non
 * questo file. Il vetro di oggi e' la lastra del banco (`kind: "glass"`) con la stessa ottica scritta per intero e
 * la stessa pellicola.
 *
 * Cosa resta fuori, e perche': le superfici di Fluid-physics (finestra del menu, finestra che si espande) e la
 * fascia `FadeDown` non passano al materiale nuovo in F1A; il palco del Playground c'e' lo stesso, per mostrare
 * cosa la sua ottica dara' quando F1B lo portera' sul motore nuovo. L'orologio resta `Classic` (piano 3.11): le sue
 * lastre sono qui solo per mostrare cosa succederebbe.
 *
 * Le scene vanno in `compatibilita/` sotto `GLASS_SCENE_DIR`, insieme alle tavole che le compongono
 * (`glassbench.py tavola`). Non si modificano a mano: si rigenerano.
 */

/** Una lastra di un caso: taglia in dp, raggio nella forma del banco, il contenuto stampato sopra. */
internal class CompatSlab(
  val name: String,
  val w: Int,
  val h: Int,
  val radius: Any,
  val content: Map<String, Any?>,
  /** L'ottica di questa lastra, se non e' quella del caso (i due tasti di Fluidify). */
  val optics: GlassOptics? = null,
)

/** Un'ottica e una tinta scritte a mano, com'e' scritta dove vive. */
internal class CompatCase(
  val key: String,
  val dark: Boolean,
  /** Quale tavola la mostra. */
  val tavola: String,
  val title: String,
  /** Dove sta, e cosa scrive: per chi legge la tavola. */
  val source: String,
  val role: GlassRole,
  /** Il ruolo del banco su cui il vetro di oggi appoggia l'ottica (che resta comunque scritta per intero). */
  val benchRole: String,
  val optics: GlassOptics,
  /** La tinta come la costruisce chi la usa. */
  val tint: GlassTint,
  /** La pellicola del vetro di oggi nel vocabolario del banco: un nome della sua palette, o `#RRGGBBAA`. */
  val classicTint: String,
  val slabs: List<CompatSlab>,
  /** I fondi delle righe della tavola. */
  val grounds: List<String>,
  val zoom: Int = 1,
)

internal object GlassCompatScenes {

  const val Folder: String = "compatibilita"
  const val Width: Int = 900

  /** L'inchiostro dell'engine sul vetro: `onSurface` di `FluidTheme`, chiaro e scuro. */
  private val InkLight = "#121214"
  private val InkDark = "#F2F2F5"

  /** L'accento di serie dell'engine (`FluidDefaultBrand`), come lo legge il banco. */
  private val AccentLight = Color(0xFF1F9E6E)
  private val AccentDark = Color(0xFF2ED8A0)

  private fun hex(color: Color): String = glassColor(color)

  /** Una tinta di serie (`Neutral`) come la costruisce `GlassDefaults`: la pellicola e' quella del banco. */
  private fun defaultTint(surface: String, dark: Boolean, fallback: Color, hairline: Color): GlassTint =
    GlassTint(todayFilm(sceneSurface(surface), dark), fallback, hairline, GlassTintMode.Neutral)

  private val interactive = GlassDefaults.optics(GlassRole.Interactive)
  private val modal = GlassDefaults.optics(GlassRole.Modal)
  private val floating = GlassDefaults.optics(GlassRole.Floating)

  /** `FluidWearGlass.Optics`, com'e' scritta: quattordici argomenti nominati. */
  private val wearOptics = GlassOptics(
    blurScale = 1.6f,
    refractionHeight = 12.dp,
    refractionAmount = 16.dp,
    depthEffect = true,
    dispersion = false,
    vibrancy = 1.5f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 14.dp,
    shadowAlpha = 0.55f,
    pressedDepthBoost = 0.5f,
  )

  /** Le tre tinte dell'orologio (`FluidWearGlass`): fumo, pagina, avviso, con un sesto dell'accento. */
  private fun wearTint(base: Color, alpha: Float): GlassTint = GlassTint(
    overlay = lerp(base, AccentDark, 0.16f).copy(alpha = alpha),
    fallback = lerp(Color(0xFF141416), AccentDark, 0.16f).copy(alpha = 0.95f),
    hairline = Color.White.copy(alpha = 0.18f),
  )

  private fun icon(name: String, ink: String, size: Int = 24): Map<String, Any?> =
    linkedMapOf("iconSizeDp" to size, "icon" to name, "contentColor" to ink)

  private fun label(text: String, ink: String, size: Int = 17): Map<String, Any?> =
    linkedMapOf("label" to text, "labelSizeDp" to size, "contentColor" to ink)

  private fun icons(names: List<String>, ink: String): Map<String, Any?> =
    linkedMapOf("iconSizeDp" to 24, "icons" to names, "contentColor" to ink)

  private fun rows(labels: List<String>, ink: String): Map<String, Any?> = linkedMapOf(
    "rowHeightDp" to 48, "labelSizeDp" to 16, "contentColor" to ink,
    "rows" to labels.map { linkedMapOf("label" to it) },
  )

  private const val Add = "fluid:add|plus"
  private const val Play = "fluid:play_arrow|plus"
  private val BarIcons = listOf("fluid:arrow_back|back", "fluid:add|plus", "fluid:circle|circle")

  /** I casi, nell'ordine delle tavole. */
  val Cases: List<CompatCase> by lazy { buildCases() }

  private fun buildCases(): List<CompatCase> {
    val out = ArrayList<CompatCase>()

    // ---- App, lato chiaro -------------------------------------------------------------------------------------
    val fluidifyControlLight = GlassTint(
      overlay = Color.White.copy(alpha = 0.40f),
      fallback = Color(0xFFFDFDFF).copy(alpha = 0.96f),
      hairline = Color.Black.copy(alpha = 0.26f),
    )
    out += CompatCase(
      key = "fluidify-tasto-su-foto", dark = false, tavola = "app-chiaro",
      title = "Fluidify: tasto su una copertina (pellicola bianca 40%)",
      source = "Fluidify, ui/components/GlassButton.kt:170 (controlTintOnPhoto) e :81 (ottica Interactive di serie)",
      role = GlassRole.Interactive, benchRole = "interactive", optics = interactive,
      tint = fluidifyControlLight, classicTint = hex(fluidifyControlLight.overlay),
      slabs = listOf(CompatSlab("tasto", 44, 44, "capsule", icon(Play, InkLight))),
      grounds = listOf("foto", "testo"),
    )
    val controlLight = defaultTint("button", false, Color.White.copy(alpha = 0.90f), Color.Transparent)
    out += CompatCase(
      key = "fluidify-tasti-in-pagina", dark = false, tavola = "app-chiaro",
      title = "Fluidify: tasto «piatto» (sfocatura 0, cattura al 40%) e tasto in pagina (cattura al 40%), tinta di serie",
      source = "Fluidify, ui/components/GlassButton.kt:98-101 (GlassDefaults.optics(Interactive).copy(...)) con controlTint()",
      role = GlassRole.Interactive, benchRole = "interactive", optics = interactive,
      tint = controlLight, classicTint = "control",
      slabs = listOf(
        CompatSlab("piatto", 44, 44, "capsule", icon(Add, InkLight), interactive.copy(blurScale = 0f, backdropResolution = 0.4f)),
        CompatSlab("in-pagina", 44, 44, "capsule", icon(Add, InkLight), interactive.copy(backdropResolution = 0.4f)),
      ),
      grounds = listOf("pagina-chiara", "testo"),
    )
    val paneLight = GlassTint(
      overlay = Color.White.copy(alpha = 0.50f),
      fallback = Color(0xFFFDFDFF).copy(alpha = 0.96f),
      hairline = Color.Black.copy(alpha = 0.24f),
    )
    out += CompatCase(
      key = "fluidify-scheda-su-foto", dark = false, tavola = "app-chiaro",
      title = "Fluidify: scheda del titolo su un video (pellicola bianca 50%, ruolo Modal)",
      source = "Fluidify, ui/components/GlassButton.kt:204 (paneTintOnPhoto), ui/player/NowPlayingPanel.kt:447",
      role = GlassRole.Modal, benchRole = "modal", optics = modal,
      tint = paneLight, classicTint = hex(paneLight.overlay),
      slabs = listOf(CompatSlab("scheda", 260, 72, 18, label("Titolo del brano", InkLight))),
      grounds = listOf("foto", "testo"),
    )
    val lightFloating = GlassTint(
      overlay = Color.White.copy(alpha = 0.64f),
      fallback = Color(0xFFFDFDFF).copy(alpha = 0.96f),
      hairline = Color.Black.copy(alpha = 0.20f),
    )
    out += CompatCase(
      key = "fluidify-pannello", dark = false, tavola = "app-chiaro",
      title = "Fluidify: il pannello del lettore (Modal con sfocatura 7, lightFloatingTint)",
      source = "Fluidify, ui/SquareApp.kt:1901-1930 (optics(Modal).copy(blurScale = 7f)), ui/player/PlayerMorph.kt:367",
      role = GlassRole.Modal, benchRole = "modal", optics = modal.copy(blurScale = 7f),
      tint = lightFloating, classicTint = hex(lightFloating.overlay),
      slabs = listOf(CompatSlab("pannello", 300, 200, 38, rows(listOf("Coda", "Testo", "Dispositivi"), InkLight))),
      grounds = listOf("foto", "testo"),
    )
    val galleryLight = GlassTint(
      overlay = Color.White.copy(alpha = 0.45f),
      fallback = Color.White.copy(alpha = 0.94f),
      hairline = Color(0xFF141414).copy(alpha = 0.16f),
    )
    out += CompatCase(
      key = "galleria-adattiva", dark = false, tavola = "app-chiaro",
      title = "MyCloud Gallery: vetro adattivo, pellicola bianca al minimo (45%)",
      source = "MyCloud-Gallery-2, core-designsystem/.../design/GalleryChrome.kt:95 (GlassTint costruita dalla legibilita')",
      role = GlassRole.Floating, benchRole = "floating", optics = floating,
      tint = galleryLight, classicTint = hex(galleryLight.overlay),
      slabs = listOf(CompatSlab("capsula", 156, 48, "capsule", icons(BarIcons, InkLight))),
      grounds = listOf("foto", "testo"),
    )

    // ---- App, lato scuro --------------------------------------------------------------------------------------
    val fluidifyControlDark = GlassTint(
      overlay = Color.Black.copy(alpha = 0.30f),
      fallback = Color(0xFF141416).copy(alpha = 0.95f),
      hairline = Color.White.copy(alpha = 0.22f),
    )
    out += CompatCase(
      key = "fluidify-tasto-su-foto", dark = true, tavola = "app-scuro",
      title = "Fluidify: tasto su una copertina, tema scuro (pellicola nera 30%)",
      source = "Fluidify, ui/components/GlassButton.kt:170 (controlTintOnPhoto)",
      role = GlassRole.Interactive, benchRole = "interactive", optics = interactive,
      tint = fluidifyControlDark, classicTint = hex(fluidifyControlDark.overlay),
      slabs = listOf(CompatSlab("tasto", 44, 44, "capsule", icon(Play, InkDark))),
      grounds = listOf("foto-scuro", "foto-buia"),
    )
    val paneDark = GlassTint(
      overlay = Color.Black.copy(alpha = 0.38f),
      fallback = Color(0xFF141416).copy(alpha = 0.95f),
      hairline = Color.White.copy(alpha = 0.20f),
    )
    out += CompatCase(
      key = "fluidify-scheda-su-foto", dark = true, tavola = "app-scuro",
      title = "Fluidify: scheda del titolo su un video, tema scuro (pellicola nera 38%)",
      source = "Fluidify, ui/components/GlassButton.kt:204 (paneTintOnPhoto)",
      role = GlassRole.Modal, benchRole = "modal", optics = modal,
      tint = paneDark, classicTint = hex(paneDark.overlay),
      slabs = listOf(CompatSlab("scheda", 260, 72, 18, label("Titolo del brano", InkDark))),
      grounds = listOf("foto-scuro", "foto-buia"),
    )
    val darkFloating = GlassTint(
      overlay = Color.Black.copy(alpha = 0.48f),
      fallback = Color(0xFF141416).copy(alpha = 0.95f),
      hairline = Color.White.copy(alpha = 0.16f),
    )
    out += CompatCase(
      key = "fluidify-pannello", dark = true, tavola = "app-scuro",
      title = "Fluidify: il pannello del lettore, tema scuro (Modal con sfocatura 7, darkFloatingTint)",
      source = "Fluidify, ui/SquareApp.kt:1927, ui/player/PlayerMorph.kt:367",
      role = GlassRole.Modal, benchRole = "modal", optics = modal.copy(blurScale = 7f),
      tint = darkFloating, classicTint = hex(darkFloating.overlay),
      slabs = listOf(CompatSlab("pannello", 300, 200, 38, rows(listOf("Coda", "Testo", "Dispositivi"), InkDark))),
      grounds = listOf("foto-scuro", "testo-scuro"),
    )
    val galleryDark = GlassTint(
      overlay = Color.Black.copy(alpha = 0.60f),
      fallback = Color(0xFF1B1B1E).copy(alpha = 0.96f),
      hairline = Color.White.copy(alpha = 0.20f),
    )
    out += CompatCase(
      key = "galleria-su-foto", dark = true, tavola = "app-scuro",
      title = "MyCloud Gallery: capsula sopra una foto, tema scuro (nero 60% per tenere il bianco a 4,5:1)",
      source = "MyCloud-Gallery-2, core-designsystem/.../chrome/GalleryTopBar.kt:83 (galleryTintOnPhoto)",
      role = GlassRole.Floating, benchRole = "floating", optics = floating,
      tint = galleryDark, classicTint = hex(galleryDark.overlay),
      slabs = listOf(CompatSlab("capsula", 156, 48, "capsule", icons(BarIcons, "#FFFFFF"))),
      grounds = listOf("foto-scuro", "foto-buia"),
    )

    // ---- L'engine: tinte di serie con un colore diverso ---------------------------------------------------------
    for (dark in listOf(false, true)) {
      val ink = if (dark) InkDark else InkLight
      val control = defaultTint(
        "button", dark, Color.White.copy(alpha = if (dark) 0.88f else 0.90f), Color.Transparent,
      )
      val tone = if (dark) Color(0xFF332000) else Color(0xFFFFF3E0)
      val sync = control.copy(overlay = tone.copy(alpha = 0.55f), fallback = tone, hairline = Color.Transparent)
      out += CompatCase(
        key = "engine-indicatore-sync", dark = dark, tavola = "engine",
        title = "Engine: l'indicatore di sincronizzazione, tono «attenzione» (controlTint().copy(overlay = tono))",
        source = "engine-ui, theme/FluidComponents.kt:698 (FluidSyncIndicatorSurface)",
        role = GlassRole.Interactive, benchRole = "button", optics = GlassDefaults.buttonOptics(),
        tint = sync, classicTint = hex(sync.overlay),
        slabs = listOf(CompatSlab("sync", 26, 26, "capsule", icon("fluid:sync|circle", ink, size = 14))),
        grounds = listOf(if (dark) "pagina-scura" else "pagina-chiara"),
        zoom = 3,
      )
    }
    for (dark in listOf(false, true)) {
      val ink = if (dark) InkDark else InkLight
      val base = defaultTint(
        "floating", dark,
        Color(if (dark) 0xFF1B1B1E else 0xFFFFFFFF).copy(alpha = if (dark) 0.96f else 0.97f),
        Color(if (dark) 0xFFF2F2F5 else 0xFF121214).copy(alpha = if (dark) 0.18f else 0.08f),
      )
      // Come la costruisce `composerTint()` (engine-ai-ui, F1A.8): per intero, col modo della flottante.
      val composer = GlassTint(
        overlay = base.overlay.copy(alpha = base.overlay.alpha * 0.80f),
        fallback = base.fallback,
        hairline = base.hairline,
        mode = base.mode,
      )
      out += CompatCase(
        key = "engine-composer", dark = dark, tavola = "engine",
        title = "Engine: il composer della chat (pellicola della flottante all'80%)",
        source = "engine-ai-ui, aiui/chat/FluidComposer.kt:381 (composerTint)",
        role = GlassRole.Floating, benchRole = "floating", optics = floating,
        tint = composer, classicTint = hex(composer.overlay),
        slabs = listOf(CompatSlab("composer", 300, 52, "capsule", label("Scrivi un messaggio", ink, size = 16))),
        grounds = listOf(if (dark) "pagina-scura" else "pagina-chiara", if (dark) "testo-scuro" else "testo"),
      )
    }
    for (dark in listOf(false, true)) {
      val ink = if (dark) InkDark else InkLight
      val accent = if (dark) AccentDark else AccentLight
      val control = defaultTint("button", dark, Color.White.copy(alpha = 0.90f), Color.Transparent)
      val selected = control.copy(
        overlay = accent.copy(alpha = 0.36f),
        fallback = accent.copy(alpha = 0.9f),
        hairline = accent.copy(alpha = 0.45f),
        mode = GlassTintMode.Fixed,
      )
      out += CompatCase(
        key = "engine-tasto-scelto", dark = dark, tavola = "engine",
        title = "Engine: un tasto di vetro scelto (accento al 36%)",
        source = "engine-ui, fluid/FluidGlassControls.kt:243 (selectionTint di glassControlSurface)",
        role = GlassRole.Interactive, benchRole = "button", optics = GlassDefaults.buttonOptics(),
        tint = selected, classicTint = hex(selected.overlay),
        slabs = listOf(CompatSlab("scelto", 44, 44, "capsule", icon(Add, ink))),
        grounds = listOf(if (dark) "pagina-scura" else "pagina-chiara"),
      )
    }

    // ---- L'orologio (resta Classic) e il palco del Playground ---------------------------------------------------
    val wide = wearOptics.copy(depthEffect = false)
    val smoke = wearTint(Color.Black, 0.42f)
    out += CompatCase(
      key = "orologio-disco", dark = true, tavola = "orologio-e-palco",
      title = "Orologio: il disco del trasporto (fumo nero 42%) - l'orologio resta Classic",
      source = "engine-wear, glass/FluidWearGlass.kt:37 e :68, glass/FluidGlassDisc.kt:101",
      role = GlassRole.Interactive, benchRole = "interactive", optics = wearOptics,
      tint = smoke, classicTint = hex(smoke.overlay),
      slabs = listOf(CompatSlab("disco", 52, 52, "capsule", icon(Play, "#FFFFFF", size = 26))),
      grounds = listOf("foto-buia", "foto-scuro"),
    )
    val page = wearTint(Color(0xFF4A4A4E), 0.36f)
    out += CompatCase(
      key = "orologio-pillola", dark = true, tavola = "orologio-e-palco",
      title = "Orologio: la pillola di una riga su una lista nera (grigio 36%) - resta Classic",
      source = "engine-wear, glass/FluidWearGlass.kt:84 (pageTint), components/FluidWearRows.kt:159",
      role = GlassRole.Interactive, benchRole = "interactive", optics = wide,
      tint = page, classicTint = hex(page.overlay),
      slabs = listOf(CompatSlab("pillola", 150, 40, "capsule", label("Riproduci", "#FFFFFF", size = 15))),
      grounds = listOf("nero", "foto-buia"),
    )
    val notice = wearTint(Color(0xFF141416), 0.88f)
    out += CompatCase(
      key = "orologio-avviso", dark = true, tavola = "orologio-e-palco",
      title = "Orologio: un avviso (quasi opaco, 88%) - resta Classic",
      source = "engine-wear, glass/FluidWearGlass.kt:97 (noticeTint), components/FluidWearToast.kt:66",
      role = GlassRole.Floating, benchRole = "floating", optics = wide,
      tint = notice, classicTint = hex(notice.overlay),
      slabs = listOf(CompatSlab("avviso", 170, 44, "capsule", label("Salvato", "#FFFFFF", size = 15))),
      grounds = listOf("nero", "foto-scuro"),
    )
    val stage = floating.copy(
      refractionHeight = 24.dp,
      refractionAmount = 40.dp,
      depthEffect = true,
      highlightWidth = 1.2.dp,
      highlightAlpha = 0.75f,
    )
    out += CompatCase(
      key = "palco-playground", dark = false, tavola = "orologio-e-palco",
      title = "Galleria: il palco del Playground (lente 24/40 dp con la cupola, bordo 0,75) - Fluid-physics, da F1B",
      source = "sample, playground/PlaygroundStage.kt:101",
      role = GlassRole.Floating, benchRole = "floating", optics = stage,
      tint = defaultTint(
        "floating", false, Color.White.copy(alpha = 0.97f), Color(0xFF121214).copy(alpha = 0.08f),
      ),
      classicTint = "floating",
      slabs = listOf(CompatSlab("pezzo", 160, 160, 40, linkedMapOf())),
      grounds = listOf("griglia", "foto"),
    )
    return out
  }

  // ---- Le scene ---------------------------------------------------------------------------------------------

  /** Dove sta ogni lastra di ogni caso, sul lato del caso: le due scene di un lato hanno la stessa disposizione. */
  internal class Placed(val case: CompatCase, val slab: CompatSlab, val name: String, val x: Int, val y: Int)

  private const val Left = 40
  private const val Gap = 24
  private const val Band = 24

  /** I casi di un lato messi uno sotto l'altro; la seconda componente e' l'altezza della tela. */
  fun layout(dark: Boolean): Pair<List<Placed>, Int> {
    val out = ArrayList<Placed>()
    var top = Band
    for (case in Cases.filter { it.dark == dark }) {
      var x = Left
      var tallest = 0
      for (slab in case.slabs) {
        out += Placed(case, slab, "${case.key}.${slab.name}", x, top)
        x += slab.w + Gap
        tallest = max(tallest, slab.h)
      }
      top += tallest + 2 * Band
    }
    return out to top
  }

  /** Il riquadro di un caso sul suo lato, in dp, con [pad] attorno. */
  fun crop(case: CompatCase, pad: Int = 16): List<Int> {
    val placed = layout(case.dark).first.filter { it.case === case }
    val x0 = placed.minOf { it.x } - pad
    val y0 = placed.minOf { it.y } - pad
    val x1 = placed.maxOf { it.x + it.slab.w } + pad
    val y1 = placed.maxOf { it.y + it.slab.h } + pad
    return listOf(x0, y0, x1 - x0, y1 - y0)
  }

  private fun opticsSpec(benchRole: String, o: GlassOptics): LinkedHashMap<String, Any?> = linkedMapOf(
    "role" to benchRole,
    "blurScale" to o.blurScale,
    "refractionHeight" to o.refractionHeight.value,
    "refractionAmount" to o.refractionAmount.value,
    "depthEffect" to o.depthEffect,
    "dispersion" to o.dispersion,
    "vibrancy" to o.vibrancy,
    "highlightWidth" to o.highlightWidth.value,
    "highlightAlpha" to o.highlightAlpha,
    "highlightAngle" to o.highlightAngle,
    "innerShadowRadius" to o.innerShadowRadius.value,
    "innerShadowAlpha" to o.innerShadowAlpha,
    "shadowRadius" to o.shadowRadius.value,
    "shadowAlpha" to o.shadowAlpha,
    "pressedDepthBoost" to o.pressedDepthBoost,
    "edgeFloor" to o.edgeFloor,
    "backdropResolution" to o.backdropResolution,
    "sizeResponse" to o.sizeResponse,
  )

  private fun head(note: String, dark: Boolean, height: Int): LinkedHashMap<String, Any?> = linkedMapOf(
    "_" to note,
    "width" to Width,
    "height" to height,
    "density" to 3.0,
    "theme" to if (dark) "dark" else "light",
    "palette" to "engine",
    "corner" to "engine",
    "background" to linkedMapOf("kind" to "color", "color" to if (dark) "#0D0D0F" else "#F2F2F7"),
  )

  private fun shape(p: Placed): LinkedHashMap<String, Any?> {
    val out = linkedMapOf<String, Any?>(
      "name" to p.name, "x" to p.x, "y" to p.y, "w" to p.slab.w, "h" to p.slab.h, "radius" to p.slab.radius,
    )
    out.putAll(p.slab.content)
    return out
  }

  private fun measures(placed: List<Placed>): List<Map<String, Any?>> = placed.map {
    linkedMapOf(
      "kind" to "body", "element" to it.name, "label" to it.name, "rows" to listOf("corpo", "fondo", "corpo-fondo", "velo"),
    )
  }

  private fun side(dark: Boolean) = if (dark) "scuro" else "chiaro"

  /** Il vetro di oggi: la lastra del banco con l'ottica scritta per intero e la pellicola di chi la usa. */
  fun today(dark: Boolean): GlassScene {
    val (placed, height) = layout(dark)
    val scene = head(
      "Scritta da GlassCompatSceneExportTest (F1A.8): le ottiche e le tinte scritte a mano che esistono davvero, " +
        "lato ${side(dark)}, col VETRO DI OGGI (Classic): ogni lastra porta la sua ottica scritta per intero e la " +
        "pellicola di chi la usa. Il gemello col materiale nuovo e' `nuovo-${side(dark)}.json`. Non si modifica a mano.",
      dark, height,
    )
    scene["elements"] = placed.map { p ->
      val el = shape(p)
      el["optics"] = opticsSpec(p.case.benchRole, p.slab.optics ?: p.case.optics)
      el["tint"] = p.case.classicTint
      el["_caso"] = "${p.case.title}. Da: ${p.case.source}"
      el
    }
    scene["measure"] = measures(placed)
    return GlassScene("$Folder/oggi-${side(dark)}.json", scene)
  }

  /** Il modo con cui il materiale nuovo legge la tinta del caso: la regola, non una scelta di questo file. */
  fun resolvedMode(case: CompatCase): GlassTintMode = glassTintLiquidMode(case.tint)

  /** Il materiale nuovo: ogni lastra dalla ricetta, con l'ottica del caso e la tinta letta dalla regola dei modi. */
  fun liquid(dark: Boolean): GlassScene {
    val (placed, height) = layout(dark)
    val scene = head(
      "Scritta dalla ricetta (GlassCompatSceneExportTest, F1A.8): le stesse lastre di `oggi-${side(dark)}.json` col " +
        "MATERIALE NUOVO, come l'engine le disegnerebbe senza che chi le usa cambi una riga: `glassRecipe` con " +
        "l'ottica del caso passata da `sanitized()`, e la tinta letta dalla regola dei modi (`glassTintLiquidMode`). " +
        "Non si modifica a mano: si rigenera.",
      dark, height,
    )
    scene["elements"] = placed.map { p ->
      val case = p.case
      val mode = resolvedMode(case)
      val optics = (p.slab.optics ?: case.optics).sanitized()
      val recipe = glassRecipe(
        role = case.role,
        widthDp = p.slab.w.toFloat(),
        heightDp = p.slab.h.toFloat(),
        darkSide = dark,
        optics = optics,
        film = if (mode == GlassTintMode.Fixed) case.tint.overlay else Color.Unspecified,
        tint = if (mode == GlassTintMode.Colored) case.tint.overlay else Color.Unspecified,
      )
      val note = "glassRecipe: ${case.title}; ${p.slab.w} x ${p.slab.h} dp, lato ${side(dark)}, tinta letta " +
        "`${mode.name}` (detta `${case.tint.mode.name}`). Da: ${case.source}"
      liquidElement(shape(p), recipe, theme = null, note = note)
    }
    scene["measure"] = measures(placed)
    return GlassScene("$Folder/nuovo-${side(dark)}.json", scene)
  }

  // ---- Le tavole --------------------------------------------------------------------------------------------

  /** Le tavole, nell'ordine: chiave, titolo, domanda. */
  val Tavole: List<Triple<String, String, String>> = listOf(
    Triple(
      "app-chiaro",
      "Compatibilita' · le app, tema chiaro: ottiche e tinte scritte a mano",
      "A sinistra il vetro di oggi, a destra il materiale nuovo come lo disegnerebbe l'engine senza che l'app cambi " +
        "una riga. Il cambio e' accettabile per ogni caso?",
    ),
    Triple(
      "app-scuro",
      "Compatibilita' · le app, tema scuro: le pellicole sopra le foto",
      "Le tinte scritte a mano restano la pellicola dipinta (Fixed): cambiano smerigliatura, lente, bordo e ombra. " +
        "Il testo resta leggibile?",
    ),
    Triple(
      "engine",
      "Compatibilita' · l'engine: tinte di serie a cui l'engine cambia il colore",
      "La regola dei modi: un colore diverso da quello di serie si vede (Fixed); il composer e' costruito neutro " +
        "di proposito. Va bene cosi'?",
    ),
    Triple(
      "orologio-e-palco",
      "Compatibilita' · l'orologio (resta Classic) e il palco del Playground (da F1B)",
      "Cosa succederebbe se l'orologio prendesse il materiale nuovo, e cosa dara' l'ottica del palco quando F1B lo " +
        "portera' sul motore nuovo.",
    ),
  )

  private val GroundLabels: Map<String, String> = mapOf(
    "pagina-chiara" to "pagina chiara #F2F2F7",
    "pagina-scura" to "pagina scura #0D0D0F",
    "bianco" to "bianco",
    "nero" to "nero",
    "testo" to "testo",
    "testo-scuro" to "testo, tema scuro",
    "foto" to "foto",
    "foto-scuro" to "foto chiara, vetro del tema scuro",
    "foto-buia" to "foto buia, vetro del tema scuro",
    "griglia" to "griglia",
  )

  /** I fondi aggiunti a quelli delle tavole di gusto: una copertina buia, il caso dell'orologio e dei lettori. */
  private val ExtraGrounds: Map<String, Any?> = linkedMapOf(
    "foto-buia" to linkedMapOf(
      "lato" to "scuro",
      "background" to linkedMapOf("kind" to "synthetic", "scaleDp" to 44, "lift" to -0.45, "contrast" to 0.8),
    ),
  )

  fun tavola(key: String, title: String, question: String, sharedGrounds: Map<String, Any?>): GlassScene {
    val grounds = LinkedHashMap<String, Any?>()
    grounds["_"] = "I fondi delle tavole di gusto (`../tavole/fondi.json`) piu' una copertina buia."
    for ((name, value) in sharedGrounds) if (!name.startsWith("_")) grounds[name] = value
    grounds.putAll(ExtraGrounds)
    val rows = ArrayList<Map<String, Any?>>()
    for (case in Cases.filter { it.tavola == key }) {
      for (ground in case.grounds) {
        require(ground in grounds) { "${case.key}: fondo sconosciuto $ground" }
        val row = linkedMapOf<String, Any?>(
          "etichetta" to "${case.title} · ${GroundLabels[ground] ?: ground}",
          "fondo" to ground,
          "ritaglio" to crop(case),
        )
        if (case.zoom > 1) {
          row["zoom"] = case.zoom
          row["density"] = 3.0
        }
        rows += row
      }
    }
    val tavola = linkedMapOf<String, Any?>(
      "titolo" to title,
      "domanda" to question,
      "nota" to "Scritta da GlassCompatSceneExportTest (F1A.8). Le colonne sono `oggi-{lato}.json` e `nuovo-{lato}.json`: " +
        "il vetro di oggi e il materiale nuovo dalla ricetta, con l'ottica e la tinta di chi le usa. Il banco non e' il " +
        "dispositivo: Skia in software, sRGB a 8 bit; la foto e' sintetica.",
      "density" to 2.0,
      "fondi" to grounds,
      "colonne" to listOf(
        linkedMapOf("lettera" to "oggi", "nome" to "il vetro di oggi (Classic)", "scena" to "oggi-{lato}.json"),
        linkedMapOf(
          "lettera" to "nuovo", "nome" to "il materiale nuovo, senza toccare chi lo usa", "scena" to "nuovo-{lato}.json",
        ),
      ),
      "righe" to rows,
    )
    return GlassScene("$Folder/tavola-$key.json", tavola)
  }

  /** Le scene dei due lati e le tavole. [gustoFolder] e' `tools/glassbench/scenes/f1a/tavole`, per i fondi. */
  fun all(gustoFolder: File): List<GlassScene> {
    val shared = GlassSceneJson.parseObject(File(gustoFolder, "fondi.json").readText(Charsets.UTF_8))
    return listOf(today(false), today(true), liquid(false), liquid(true)) +
      Tavole.map { (key, title, question) -> tavola(key, title, question, shared) }
  }
}
