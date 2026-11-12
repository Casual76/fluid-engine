package dev.antigravity.fluidengine.sample.parity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialBackdrop
import dev.antigravity.fluidengine.sample.material.MaterialConfig
import dev.antigravity.fluidengine.sample.material.MaterialScene
import dev.antigravity.fluidengine.sample.material.MaterialStage
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.sample.measure.MeasureClock
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.fluidStaticGlassSurface
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidFormPresets
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.delay

/** Il tag di logcat della pagina: `adb logcat -s FluidParita`. */
internal const val ParityLogTag = "FluidParita"

/** Il fondo sotto le superfici: lo stesso palco della pagina «Materiale». */
internal enum class ParityScene(val key: String, val title: String, val backdrop: MaterialBackdrop) {
  Flat("piatto", "fondo piatto", MaterialBackdrop.Page),
  Text("testo", "testo", MaterialBackdrop.Text),
  Photo("foto", "foto", MaterialBackdrop.Photo),
}

/**
 * Ciò che la pagina mostra: il fondo, il tema, se le superfici di Fluid-physics «respirano» (un piccolo viaggio di
 * andata e ritorno, per vedere fermo ↔ viaggio) e quale vetro chiede la pagina (`LocalFluidGlassModel`).
 */
internal data class ParityConfig(
  val scene: ParityScene = ParityScene.Flat,
  val dark: Boolean = false,
  val breathe: Boolean = false,
  val model: FluidGlassModel = FluidGlassModel.Liquid,
) {
  /** Una riga leggibile, per logcat e per il sottotitolo. */
  val key: String
    get() = "${scene.key} · ${if (dark) "scuro" else "chiaro"} · respiro ${if (breathe) "acceso" else "spento"} · ${model.name}"

  companion object {
    const val ExtraScene = "parity.scene"
    const val ExtraTheme = "parity.theme"
    const val ExtraBreathe = "parity.breathe"
    const val ExtraModel = "parity.model"

    /**
     * Gli extra di lancio (solo nella build di debug). Un valore sconosciuto lascia quello di serie; senza
     * `parity.model` il vetro è [inherited], quello che la galleria chiede (`gallery.model`).
     */
    fun parse(inherited: FluidGlassModel, extra: (String) -> String?): ParityConfig {
      fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
      val scene = when (value(ExtraScene)) {
        "piatto", "flat", "page" -> ParityScene.Flat
        "testo", "text" -> ParityScene.Text
        "foto", "photo" -> ParityScene.Photo
        else -> ParityScene.Flat
      }
      val dark = value(ExtraTheme) in setOf("scuro", "dark")
      val breathe = value(ExtraBreathe) in setOf("on", "true", "acceso", "si", "sì")
      val model = when (value(ExtraModel)) {
        "classic" -> FluidGlassModel.Classic
        "liquid" -> FluidGlassModel.Liquid
        else -> inherited
      }
      return ParityConfig(scene, dark, breathe, model)
    }
  }
}

/** Una taglia della pagina: la stessa forma, lo stesso ruolo e la stessa ottica per tutte le strade. */
private class ParitySize(
  val key: String,
  val title: String,
  val width: Dp,
  val height: Dp,
  val radius: Dp?,
  val role: GlassRole,
  val optics: GlassOptics,
) {
  val shape: Shape = if (radius == null) FluidCapsuleShape else ContinuousCornerShape(radius)
}

private val ParitySizes = listOf(
  ParitySize("tasto", "tasto · 44 × 44 · Interactive", 44.dp, 44.dp, null, GlassRole.Interactive, GlassDefaults.buttonOptics()),
  ParitySize("capsula", "capsula · 160 × 44 · Floating", 160.dp, 44.dp, null, GlassRole.Floating, GlassDefaults.optics(GlassRole.Floating)),
  ParitySize("pannello", "pannello · 200 × 120, angolo 28 · Modal", 200.dp, 120.dp, 28.dp, GlassRole.Modal, GlassDefaults.panelOptics()),
)

/**
 * Lo spazio attorno a una superficie di Fluid-physics: il suo layer è ritagliato sul nodo, e l'ombra nel corpo si vede
 * solo dentro il nodo. Il nodo è più grande della forma di tanto per lato (come fa la barra fra le pagine con
 * `glassTravelShadowReachDp`), e i riquadri stanno abbastanza lontani da non sovrapporsi.
 */
private val ShadowRoom = 48.dp

/** Una tinta che lascia il velo al materiale, uguale per tutte le strade che ne prendono una. */
private val ParityTint = GlassTint(
  overlay = Color.Transparent,
  fallback = Color(0xEBF2F2F7),
  hairline = Color.Transparent,
  mode = GlassTintMode.Neutral,
)

/**
 * La pagina «Parità» (F1B.6): la stessa forma resa dalle strade diverse del materiale, **fianco a fianco sullo stesso
 * fondo**: `glassSurface` | la stessa forma come superficie di Fluid-physics ferma | il vetro dipinto; e sotto la
 * pillola della tab bar e il pomello della goccia accanto a un tasto di `glassSurface`. È la parità al pixel del piano
 * (4.4), da guardare sul tablet; quella sugli ingressi la prova `GlassRecipeParityTest` sul JVM.
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page parity --es parity.scene foto --es parity.theme scuro --es parity.breathe on
 *     adb logcat -s FluidParita
 *
 * @param stage il fondo della galleria, se c'è (come per «Materiale»): la barra delle schede sopra la pagina lo sfoca.
 */
@Composable
internal fun ParityPage(bottomInset: Dp, stage: GlassBackdropState? = null, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val debuggable = remember(context) { context.isDebuggableApp() }
  val inherited = LocalFluidGlassModel.current
  val launched = remember(activity, inherited) {
    if (debuggable && activity != null) {
      ParityConfig.parse(inherited) { activity.intent?.getStringExtra(it) }
    } else {
      ParityConfig(model = inherited)
    }
  }
  var config by remember { mutableStateOf(launched) }
  val ownStage = rememberGlassBackdrop()
  val glassStage = stage ?: ownStage
  val clock = remember { MeasureClock() }
  // Il censimento delle superfici per materiale (`liveSurfaces`) conta solo con la diagnostica accesa: la pagina la
  // accende prima di comporre i riquadri (in composizione, non in un effetto, perché conti anche i primi) e la rimette
  // com'era quando esce. Solo nella build di debug.
  val wasEnabled = remember {
    val was = FluidGlassDiagnostics.enabled
    if (debuggable) FluidGlassDiagnostics.enabled = true
    was
  }
  DisposableEffect(Unit) { onDispose { FluidGlassDiagnostics.enabled = wasEnabled } }

  FluidTheme(settings = EngineSettings(themeMode = if (config.dark) ThemeMode.DARK else ThemeMode.LIGHT), systemBars = false) {
    CompositionLocalProvider(
      LocalFluidGlassModel provides config.model,
      LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      key(config) {
        Box(modifier = Modifier.fillMaxSize().testTag("parity-page")) {
          MaterialStage(
            config = MaterialConfig.defaults(MaterialScene.Components).copy(backdrop = config.scene.backdrop, hud = false),
            clock = clock,
            stage = glassStage,
          ) {
            ParityGrid(config, glassStage, bottomInset, onBack = onBack, onChange = { config = it })
          }
        }
        ParityLog(config)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParityGrid(
  config: ParityConfig,
  stage: GlassBackdropState,
  bottomInset: Dp,
  onBack: () -> Unit,
  onChange: (ParityConfig) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .statusBarsPadding()
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + FluidFloatingTabBarDefaults.ContentInset + 16.dp),
  ) {
    Text("Parità", style = MaterialTheme.typography.headlineSmall)
    Text("F1B.6 · ${config.key}", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FluidGlassButton(text = "Indietro", onClick = onBack)
      FluidGlassButton(
        text = "Fondo: ${config.scene.title}",
        onClick = { onChange(config.copy(scene = ParityScene.entries[(config.scene.ordinal + 1) % ParityScene.entries.size])) },
        modifier = Modifier.testTag("parity-scene"),
      )
      FluidGlassButton(
        text = "Tema: ${if (config.dark) "scuro" else "chiaro"}",
        onClick = { onChange(config.copy(dark = !config.dark)) },
        modifier = Modifier.testTag("parity-theme"),
      )
      FluidGlassButton(
        text = "Respiro: ${if (config.breathe) "acceso" else "spento"}",
        onClick = { onChange(config.copy(breathe = !config.breathe)) },
        modifier = Modifier.testTag("parity-breathe"),
      )
      FluidGlassButton(
        text = "Vetro: ${config.model.name}",
        onClick = {
          onChange(config.copy(model = if (config.model == FluidGlassModel.Liquid) FluidGlassModel.Classic else FluidGlassModel.Liquid))
        },
      )
    }
    for (size in ParitySizes) {
      Spacer(Modifier.height(12.dp))
      Text(size.title, style = MaterialTheme.typography.titleSmall)
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        ParityCell("glassSurface", size) {
          Box(
            modifier = Modifier
              .size(size.width, size.height)
              .glassSurface(state = stage, tint = ParityTint, shape = size.shape, role = size.role, optics = size.optics),
          )
        }
        ParityCell("Fluid-physics ferma", size) { PhysicsSlab(size, stage, config.breathe) }
        ParityCell("vetro dipinto", size) {
          Box(modifier = Modifier.size(size.width, size.height).fluidStaticGlassSurface(shape = size.shape))
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Text("pillola e goccia, accanto a un tasto di glassSurface", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(ShadowRoom / 2))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(24.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      val reference = ParitySizes.first()
      Box(
        modifier = Modifier
          .size(reference.width, reference.height)
          .glassSurface(state = stage, tint = ParityTint, shape = reference.shape, role = reference.role, optics = reference.optics),
      )
      var selected by remember { mutableStateOf("casa") }
      val items = remember {
        listOf(FluidTabItem("casa", "Casa", FluidIcons.Home), FluidTabItem("cerca", "Cerca", FluidIcons.Search))
      }
      Box(modifier = Modifier.width(260.dp).testTag("parity-puck")) {
        FluidTabBar(items = items, selectedRoute = selected, onSelect = { selected = it.route }, backdrop = stage)
      }
      var on by remember { mutableStateOf(true) }
      FluidSwitch(checked = on, onCheckedChange = { on = it }, backdrop = stage, modifier = Modifier.testTag("parity-switch"))
      var value by remember { mutableFloatStateOf(0.5f) }
      FluidSlider(value = value, onValueChange = { value = it }, modifier = Modifier.width(200.dp).testTag("parity-slider"))
    }
    Spacer(Modifier.height(ShadowRoom / 2))
    Text(
      "Le strade di ogni riquadro sono in logcat (tag FluidParita). Il riquadro di Fluid-physics ha attorno " +
        "${ShadowRoom.value.toInt()} dp di nodo per l'ombra.",
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

/** Un riquadro: il nome della strada sopra, e attorno lo spazio per l'ombra di Fluid-physics. */
@Composable
private fun ParityCell(label: String, size: ParitySize, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier.widthIn(min = size.width + ShadowRoom * 2),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(ShadowRoom))
    content()
    Spacer(Modifier.height(ShadowRoom))
  }
}

/**
 * La stessa forma come superficie di Fluid-physics: ferma, o che respira (si stringe di poco e torna, una volta ogni
 * due secondi). Il nodo è più grande della forma di [ShadowRoom] per lato; la forma sta al centro.
 */
@Composable
private fun PhysicsSlab(size: ParitySize, stage: GlassBackdropState, breathe: Boolean) {
  val density = LocalDensity.current
  val room = with(density) { ShadowRoom.toPx() }
  val w = with(density) { size.width.toPx() }
  val h = with(density) { size.height.toPx() }
  val radius = size.radius?.let { with(density) { it.toPx() } }
  fun form(inset: Float): FluidForm {
    val dx = w * inset
    val dy = h * inset
    val frame = Rect(room + dx, room + dy, room + w - dx, room + h - dy)
    return if (radius == null) {
      FluidFormPresets.capsule(frame)
    } else {
      FluidForm.Slab(frame, FluidCornerRadii.all(radius * (1f - 2f * inset)), ContinuousCornerShape.IosSmoothing)
    }
  }
  val rest = remember(w, h, radius, room) { form(0f) }
  val state = rememberFluidPhysicsState(rest)
  LaunchedEffect(breathe, rest) {
    state.snapTo(rest)
    if (!breathe) return@LaunchedEffect
    val small = form(0.07f)
    while (true) {
      delay(900)
      state.morphTo(small)
      state.morphTo(rest)
    }
  }
  Box(modifier = Modifier.size(size.width, size.height)) {
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .requiredSize(size.width + ShadowRoom * 2, size.height + ShadowRoom * 2)
        .fluidPhysicsSurface(state = state, backdrop = stage, tint = ParityTint, role = size.role, optics = size.optics),
    )
  }
}

/**
 * La strada che ogni riquadro prende, in logcat. La scelta vera sta dentro l'engine e non si legge riquadro per
 * riquadro: qui si scrive quella che le regole del codice danno con gli ingressi della pagina (il vetro chiesto, il
 * livello di API effettivo, cioè quello simulato se c'è, e i verdetti degli shader già chiesti), e dopo un secondo e
 * mezzo il censimento della diagnostica (`liveSurfaces`), che conta anche i tasti della pagina e le barre della
 * galleria. Se il censimento non torna con le righe, la regola scritta qui non è più quella del codice.
 */
@Composable
private fun ParityLog(config: ParityConfig) {
  LaunchedEffect(config) {
    val api = FluidGlassDiagnostics.simulatedApiLevel ?: Build.VERSION.SDK_INT
    Log.i(ParityLogTag, "AVVIO ${config.key} | API effettiva $api (vera ${Build.VERSION.SDK_INT})")
    delay(1500)
    val verdicts = FluidGlassDiagnostics.shaderVerdicts
    fun sane(name: String): Boolean = verdicts[name] == null
    val wantsLiquid = config.model == FluidGlassModel.Liquid && api >= Build.VERSION_CODES.TIRAMISU
    val slab = if (wantsLiquid && sane("GlassBody1ShaderString") && sane("GlassLight1ShaderString")) "Liquid" else "Classic"
    val physics = when {
      wantsLiquid && sane("GlassBody8ShaderString") && sane("GlassBodyRingShaderString") -> "Liquid (Full)"
      api >= Build.VERSION_CODES.TIRAMISU -> "vetro di prima (Full)"
      api >= Build.VERSION_CODES.S -> "vetro di prima (Balanced)"
      else -> "vetro di prima (Lite)"
    }
    for (size in ParitySizes) {
      Log.i(ParityLogTag, "riquadro ${size.key}/glassSurface: $slab")
      Log.i(ParityLogTag, "riquadro ${size.key}/fisica: $physics")
      Log.i(ParityLogTag, "riquadro ${size.key}/dipinto: ricetta (nessuna scelta del modello: vale anche con Classic e sotto Android 13)")
    }
    Log.i(ParityLogTag, "riquadro pillola: ${if (slab == "Liquid") "Liquid (GlassPuckProfile)" else "pillola di prima"}")
    Log.i(ParityLogTag, "riquadro goccia: ${if (slab == "Liquid") "Liquid (GlassDropProfile)" else "goccia di Kyant"}")
    val census = if (FluidGlassDiagnostics.enabled) FluidGlassDiagnostics.liveSurfaces.entries.joinToString { "${it.key}=${it.value}" } else "diagnostica spenta"
    Log.i(ParityLogTag, "censimento: $census")
    val refused = verdicts.filterValues { it != null }.keys
    Log.i(ParityLogTag, "shader rifiutati: ${if (refused.isEmpty()) "nessuno" else refused.joinToString()}")
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
