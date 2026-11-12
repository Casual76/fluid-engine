package dev.antigravity.fluidengine.sample.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.MoreVert
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.sample.measure.generateMeasurePhoto
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * La pagina «Adattività» (piano «iOS in Android», F2.6): dove si prova e si riprende dal tablet tutto ciò che F2 ha dato
 * all'engine. Il vetro si adatta a ciò che ha dietro (sonda, velo e ombra, giro chiaro/scuro con l'inchiostro, tasto
 * tinto, `Clear`, cursore dell'utente), la fascia in cima alle schermate è diventata il bordo dello scorrimento, e il
 * collo liquido dura.
 *
 * Sette scene, ognuna con il suo extra di lancio (`adaptive.scene`) e a mano dal tasto «Scena»: le leggi e i comandi
 * sono nel `LEGGIMI.md` della cartella. In logcat (`adb logcat -s FluidAdattivo`) la pagina scrive ciò che serve a chi
 * misura: `SCENA`, `RIQUADRO` (lato di ogni vetro), `FONDO` (quando cambia il fondo, in `SystemClock.uptimeMillis()`),
 * `LETTURA` (ogni lettura di una sonda in più, col lato che il filtro ha deciso), `ASPETTO` (il lato del vetro visto da
 * dentro), `COPERTURA`, `SONDA` (contatori e motivi dei risvegli).
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page adaptive --es adaptive.scene bianco-nero --es adaptive.auto true
 *     adb logcat -s FluidAdattivo
 *
 * **Ferma, la pagina non scrive niente** (nessuno stato, nessuna lettura oltre le prime): `GlassProbeQuietTest` la conta
 * come le altre. Solo con `adaptive.auto true` qualcosa si muove da solo.
 */
@Composable
internal fun AdaptivePage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val debuggable = remember(context) { context.isDebuggableApp() }
  val inheritedModel = LocalFluidGlassModel.current
  val inheritedDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val launched = remember(activity, inheritedModel) {
    if (debuggable && activity != null) {
      AdaptiveLaunch.parse(inheritedDark, inheritedModel) { activity.intent?.getStringExtra(it) }
    } else {
      AdaptiveConfig(dark = inheritedDark, model = inheritedModel) to AdaptiveStart()
    }
  }
  var config by remember { mutableStateOf(launched.first) }
  val live = remember { AdaptiveLive(launched.second) }
  val report = remember { AdaptiveReport() }

  FluidTheme(settings = EngineSettings(themeMode = if (config.dark) ThemeMode.DARK else ThemeMode.LIGHT), systemBars = false) {
    CompositionLocalProvider(
      LocalFluidGlassModel provides config.model,
      LocalFluidGlassPreference provides live.cursor,
      LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      // La scena rinasce solo se cambia lei, il tema, il vetro chiesto o la variante: il fondo, la quota e il cursore no.
      key(config) {
        Box(modifier = Modifier.fillMaxSize().testTag("adaptive-page")) {
          AdaptiveScenes(config, live, report, bottomInset, onBack = onBack, onChange = { config = it })
        }
        LaunchedEffect(config) {
          Log.i(
            AdaptiveLogTag,
            "SCENA ${config.key} · API ${Build.VERSION.SDK_INT} · sonda ${if (FluidGlassProbeDiagnostics.isAvailable) "disponibile" else "assente"} · " +
              "automatico ${if (live.auto) "acceso" else "spento"} · cursore ${"%.2f".format(live.cursor)}",
          )
          // I riquadri si posano dopo il primo fotogramma: una riga sola, perché chi ritaglia prenda lo stesso punto.
          delay(1_500L)
          report.logBoxes()
        }
      }
      ProbeCountersLog(config.scene.key)
    }
  }
}

/** La scena chiesta da [config], con l'intestazione dei comandi. */
@Composable
private fun AdaptiveScenes(
  config: AdaptiveConfig,
  live: AdaptiveLive,
  report: AdaptiveReport,
  bottomInset: Dp,
  onBack: () -> Unit,
  onChange: (AdaptiveConfig) -> Unit,
) {
  when (config.scene) {
    AdaptiveScene.Scroll -> ScrollScene(config, live, bottomInset, onBack, onChange)
    else -> StageScene(config, live, report, bottomInset, onBack, onChange)
  }
}

// ------------------------------------------------------------------------------------------------------------
// I comandi
// ------------------------------------------------------------------------------------------------------------

/** La riga dei comandi in cima a ogni scena: la scena, il tema, il vetro chiesto e quelli propri della scena. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
  config: AdaptiveConfig,
  live: AdaptiveLive,
  onBack: () -> Unit,
  onChange: (AdaptiveConfig) -> Unit,
) {
  Text("Adattività", style = MaterialTheme.typography.headlineSmall)
  Text("F2.6 · ${config.key}", style = MaterialTheme.typography.bodySmall)
  Spacer(Modifier.height(8.dp))
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidGlassButton(text = "Indietro", onClick = onBack)
    FluidGlassButton(
      text = "Scena: ${config.scene.title}",
      onClick = { onChange(config.copy(scene = config.scene.next())) },
      modifier = Modifier.testTag("adaptive-scene"),
    )
    FluidGlassButton(
      text = "Tema: ${if (config.dark) "scuro" else "chiaro"}",
      onClick = { onChange(config.copy(dark = !config.dark)) },
      modifier = Modifier.testTag("adaptive-theme"),
    )
    FluidGlassButton(
      text = "Vetro: ${config.model.name}",
      onClick = {
        onChange(config.copy(model = if (config.model == FluidGlassModel.Liquid) FluidGlassModel.Classic else FluidGlassModel.Liquid))
      },
      modifier = Modifier.testTag("adaptive-model"),
    )
    FluidGlassButton(
      text = "Automatico: ${if (live.auto) "acceso" else "spento"}",
      onClick = { live.auto = !live.auto },
      modifier = Modifier.testTag("adaptive-auto"),
    )
    when (config.scene) {
      AdaptiveScene.Flip -> FluidGlassButton(
        text = "Fondo: ${live.tone.title}",
        onClick = { live.tone = AdaptiveTone.entries[(live.tone.ordinal + 1) % AdaptiveTone.entries.size] },
        modifier = Modifier.testTag("adaptive-tone"),
      )
      AdaptiveScene.Checker -> {
        FluidGlassButton(
          text = "Riquadri scuri: ${live.dark.title}",
          onClick = { live.dark = live.dark.next() },
          modifier = Modifier.testTag("adaptive-dark"),
        )
        FluidGlassButton(
          text = "Casella: ${live.cell} dp",
          onClick = { live.cell = when { live.cell < 24 -> 32; live.cell < 48 -> 64; else -> 16 } },
          modifier = Modifier.testTag("adaptive-cell"),
        )
      }
      AdaptiveScene.Cursor -> {
        FluidGlassButton(text = "Trasparente", onClick = { live.cursor = FluidGlassPreference.Transparent }, modifier = Modifier.testTag("adaptive-cursor-0"))
        FluidGlassButton(text = "Di serie", onClick = { live.cursor = FluidGlassPreference.Default }, modifier = Modifier.testTag("adaptive-cursor-mid"))
        FluidGlassButton(text = "Colorato", onClick = { live.cursor = FluidGlassPreference.Colored }, modifier = Modifier.testTag("adaptive-cursor-1"))
      }
      AdaptiveScene.Threshold, AdaptiveScene.Large -> Unit
      AdaptiveScene.Scroll, AdaptiveScene.Variants -> Unit
    }
  }
}

// ------------------------------------------------------------------------------------------------------------
// Le scene sul palco (tutte tranne lo scorrimento)
// ------------------------------------------------------------------------------------------------------------

private val DemoTabs = listOf(
  FluidTabItem(route = "home", label = "Home", icon = FluidIcons.Home, filledIcon = FluidIcons.Filled.Home),
  FluidTabItem(route = "search", label = "Cerca", icon = FluidIcons.Search, filledIcon = FluidIcons.Filled.Search),
  FluidTabItem(route = "settings", label = "Altro", icon = FluidIcons.Settings, filledIcon = FluidIcons.Filled.Settings),
)

private val DemoActions = listOf(
  FluidContextAction("Condividi", FluidIcons.Share) {},
  FluidContextAction("Aggiungi ai preferiti", FluidIcons.Filled.Favorite) {},
  FluidContextAction("Elimina", FluidIcons.Delete, destructive = true) {},
)

private val White = Color.White
private val Black = Color.Black

/** Il disegno del fondo di una scena sul palco, letto in disegno: cambiarlo non ricompone niente. */
private fun fundOf(config: AdaptiveConfig, live: AdaptiveLive, density: Float, photo: Brush?): DrawScope.() -> Unit = {
  when (config.scene) {
    AdaptiveScene.Flip -> drawRect(argb(live.tone.argb))
    AdaptiveScene.Threshold, AdaptiveScene.Large -> {
      drawRect(White)
      val edge = adaptiveEdge(live.share, size.height)
      drawRect(Black, topLeft = Offset(0f, edge), size = Size(size.width, size.height - edge))
    }
    AdaptiveScene.Checker -> {
      val cell = live.cell * density
      val shift = if (live.phase == 1) cell * 0.37f else 0f
      drawChecker(cellPx = cell, light = White, dark = argb(live.dark.argb), shiftPx = shift)
    }
    AdaptiveScene.Cursor -> if (photo != null) drawRect(photo) else drawRect(Color(0xFF6B5A44))
    AdaptiveScene.Variants -> {
      // Sopra una foto chiara (la foto con un velo bianco), sotto una scura (con un velo nero).
      if (photo != null) drawRect(photo) else drawRect(Color(0xFF6B5A44))
      val half = size.height / 2f
      drawRect(White.copy(alpha = 0.58f), topLeft = Offset.Zero, size = Size(size.width, half))
      drawRect(Black.copy(alpha = 0.66f), topLeft = Offset(0f, half), size = Size(size.width, size.height - half))
    }
    AdaptiveScene.Scroll -> Unit
  }
}

/** La foto sintetica della galleria come pennello che si ripete ogni 512 dp. */
@Composable
private fun rememberPhotoBrush(): Brush {
  val density = LocalDensity.current
  return remember(density) {
    val image = generateMeasurePhoto()
    val period = with(density) { 512.dp.toPx() }
    ShaderBrush(
      ImageShader(image, TileMode.Repeated, TileMode.Repeated).apply {
        val scale = period / image.width.toFloat()
        setLocalMatrix(Matrix().apply { setScale(scale, scale) })
      },
    )
  }
}

@Composable
private fun StageScene(
  config: AdaptiveConfig,
  live: AdaptiveLive,
  report: AdaptiveReport,
  bottomInset: Dp,
  onBack: () -> Unit,
  onChange: (AdaptiveConfig) -> Unit,
) {
  val stage = rememberGlassBackdrop()
  val probeSource = rememberLayerBackdrop()
  val density = LocalDensity.current.density
  val photo = rememberPhotoBrush()
  val morph = rememberFluidMorphMenuState()
  val modalHost = rememberFluidGlassModalHostState()
  val draw = remember(config, live, density, photo) { fundOf(config, live, density, photo) }

  AutomaticMotion(config, live)
  FundLog(config, live, report)

  AdaptiveStage(stage, probeSource, report, draw) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .statusBarsPadding()
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + FluidFloatingTabBarDefaults.ContentInset + 16.dp),
    ) {
      Controls(config, live, onBack, onChange)
      Spacer(Modifier.height(8.dp))
      when (config.scene) {
        AdaptiveScene.Flip, AdaptiveScene.Threshold, AdaptiveScene.Checker -> {
          if (config.scene == AdaptiveScene.Threshold) ShareSlider(live, "Quota di scuro dal basso")
          Caption(sceneCaption(config.scene))
          GlassSet(stage, probeSource, report, morph, tabBar = true)
        }
        AdaptiveScene.Large -> CompositionLocalProvider(LocalFluidGlassModalHostState provides modalHost) {
          LargeScene(stage, probeSource, report, live)
        }
        AdaptiveScene.Cursor -> CursorScene(stage, probeSource, report, live)
        AdaptiveScene.Variants -> VariantsScene(stage, probeSource, report)
        AdaptiveScene.Scroll -> Unit
      }
    }
    FluidMorphMenuHost(state = morph, backdrop = stage)
    // Il foglio della scena dei grandi si disegna qui, dentro il palco e sotto il suo vetro (come nella pagina «Materiale»).
    if (config.scene == AdaptiveScene.Large) FluidGlassModalHost(state = modalHost, backdrop = stage)
  }
}

private fun sceneCaption(scene: AdaptiveScene): String = when (scene) {
  AdaptiveScene.Flip ->
    "Il vetro sta fermo e cambia il fondo (bianco, grigio, nero e ritorno). A mano col tasto «Fondo»; con «Automatico» ogni 2 s."
  AdaptiveScene.Threshold ->
    "Il fondo è bianco e una fascia nera sale dal basso: la quota si regola col cursore, o sale e scende da sola con «Automatico». " +
      "Le righe COPERTURA dicono quanto di ogni vetro è sopra il nero."
  AdaptiveScene.Checker ->
    "Una scacchiera di chiaro e scuro: i vetri non devono girare avanti e indietro. Con «Automatico» il disegno si sposta di " +
      "una frazione di casella ogni 600 ms, così la media sotto il vetro oscilla attorno alle soglie."
  else -> ""
}

@Composable
private fun Caption(text: String) {
  if (text.isEmpty()) return
  Text(text, style = MaterialTheme.typography.bodySmall)
  Spacer(Modifier.height(12.dp))
}

@Composable
private fun ShareSlider(live: AdaptiveLive, label: String) {
  Text("$label: ${"%.0f".format(live.share * 100)}%", style = MaterialTheme.typography.labelLarge)
  FluidSlider(
    value = live.share,
    onValueChange = { live.share = it },
    modifier = Modifier.fillMaxWidth().testTag("adaptive-share"),
  )
  Spacer(Modifier.height(8.dp))
}

/**
 * I vetri che si guardano nelle prime tre scene, fermi: un tasto tondo, una capsula con tre icone, un tasto tinto, un menu
 * aperto (un pannello della taglia e del ruolo del menu), una barra con la tab bar, e il tasto che diventa menu.
 */
@Composable
private fun GlassSet(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
  morph: dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState,
  tabBar: Boolean,
) {
  val tabScroll = rememberFluidFloatingTabBarScrollConnection()
  var tab by remember { mutableStateOf(DemoTabs.first().route) }
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidGlassIconButton(
        onClick = {},
        backdrop = stage,
        modifier = Modifier.then(report.place("tondo")).adaptiveProbe(probeSource, "tondo"),
      ) {
        FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito")
        AppearanceLog("tondo")
      }
      TintedSlab(stage, probeSource, report, "tinto")
    }
    FluidFloatingCapsule(
      modifier = Modifier.then(report.place("capsula")).adaptiveProbe(probeSource, "capsula"),
      backdrop = stage,
    ) {
      FluidCapsuleAction(FluidIcons.Share, "Condividi", onClick = {})
      FluidCapsuleAction(FluidIcons.Filled.Favorite, "Preferito", onClick = {})
      FluidCapsuleAction(FluidIcons.Delete, "Elimina", onClick = {}, destructive = true)
    }
    MenuPanel(stage, probeSource, report, "menu", width = 200.dp, height = 168.dp, compact = true)
    if (tabBar) {
      FluidFloatingTabBar(
        items = DemoTabs,
        selectedRoute = tab,
        onSelect = { tab = it.route },
        scrollConnection = tabScroll,
        backdrop = stage,
        modifier = Modifier.then(report.place("tabbar")).adaptiveProbe(probeSource, "tabbar"),
      )
    }
    FluidMorphMenuButton(
      state = morph,
      actions = { DemoActions },
      onClick = {},
      text = "Menu che si apre",
      icon = FluidIcons.MoreVert,
      backdrop = stage,
      opensOnTap = true,
      modifier = Modifier.then(report.place("tasto-menu")),
    )
  }
}

/** Un tasto tinto alla Apple (`GlassTintMode.Colored`, l'accento sopra il velo che segue lo sfondo), con la sua etichetta. */
@Composable
private fun TintedSlab(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
  name: String,
  width: Dp = 120.dp,
) {
  val accent = MaterialTheme.colorScheme.primary
  val tint = remember(accent) {
    GlassTint(overlay = accent.copy(alpha = 0.5f), fallback = accent.copy(alpha = 0.92f), hairline = Color.Transparent, mode = GlassTintMode.Colored)
  }
  Box(
    modifier = Modifier
      .size(width, 48.dp)
      .then(report.place(name))
      .adaptiveProbe(probeSource, name)
      .glassSurface(state = stage, tint = tint, shape = FluidCapsuleShape, role = GlassRole.Floating),
    contentAlignment = Alignment.Center,
  ) {
    Text("Tinto", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    AppearanceLog(name)
  }
}

/** Un pannello della taglia e del ruolo di un menu aperto (`Modal`, ottica del pannello), con tre righe. */
@Composable
private fun MenuPanel(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
  name: String,
  width: Dp,
  height: Dp,
  compact: Boolean,
) {
  Column(
    modifier = Modifier
      .size(width, height)
      .then(report.place(name))
      .adaptiveProbe(probeSource, name)
      .glassSurface(
        state = stage,
        tint = GlassDefaults.panelTint(),
        shape = ContinuousCornerShape(if (compact) 28.dp else FluidRadius.Sheet),
        role = GlassRole.Modal,
        optics = GlassDefaults.panelOptics(compact = compact),
      )
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text("Condividi", style = MaterialTheme.typography.bodyLarge)
    Text("Preferiti", style = MaterialTheme.typography.bodyLarge)
    Text("Elimina", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    AppearanceLog(name)
  }
}

// ------------------------------------------------------------------------------------------------------------
// I grandi
// ------------------------------------------------------------------------------------------------------------

/**
 * Un menu e un foglio aperti sopra un fondo bianco con una fascia nera che sale dal basso (la quota si regola). I grandi non
 * girano; la ricetta li fa **scurire in continuo** con la quota di nero, ma solo se il contenuto segue l'inchiostro (`GlassInk`),
 * e nessun pannello grande dell'engine lo fa: si vede il comportamento di oggi.
 */
@Composable
private fun LargeScene(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
  live: AdaptiveLive,
) {
  var sheetOpen by remember { mutableStateOf(true) }
  ShareSlider(live, "Quota di nero dal basso")
  Caption(
    "I grandi non girano. La ricetta li fa scurire in continuo con la quota di nero (da 143 a 48 su nero pieno in circa " +
      "1,5 s), ma solo se il loro contenuto segue l'inchiostro del vetro, e nessun pannello grande dell'engine lo fa ancora: " +
      "questi, di serie, tengono il lato del tema. Il menu sta al centro, il foglio in fondo.",
  )
  FluidGlassButton(
    text = if (sheetOpen) "Chiudi il foglio" else "Apri il foglio",
    onClick = { sheetOpen = !sheetOpen },
    modifier = Modifier.testTag("adaptive-sheet"),
  )
  Spacer(Modifier.height(16.dp))
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    MenuPanel(stage, probeSource, report, "menu-grande", width = 240.dp, height = 220.dp, compact = false)
  }
  FluidGlassModalPortal(
    visible = sheetOpen,
    onDismissRequest = { sheetOpen = false },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Foglio di prova",
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Foglio di prova", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidGlassButton(text = "Conferma", onClick = {})
        FluidGlassIconButton(onClick = {}) { FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito") }
      }
      Text("Il lato del foglio segue la quota di nero.", style = MaterialTheme.typography.bodyMedium)
      AppearanceLog("foglio")
      Spacer(Modifier.height(80.dp))
    }
  }
  LaunchedEffect(live, report) {
    snapshotFlow { (live.share * 20f).roundToInt() }.collect { report.logCoverage(live.share) }
  }
}

// ------------------------------------------------------------------------------------------------------------
// Il cursore dell'utente e le varianti
// ------------------------------------------------------------------------------------------------------------

/**
 * Il cursore dell'utente (`LocalFluidGlassPreference`): vetro di serie sopra una foto, e sotto la riga di
 * `fluidAppearanceSection` collegata al cursore, coi due estremi anche a portata di tocco nella riga dei comandi.
 */
@Composable
private fun CursorScene(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
  live: AdaptiveLive,
) {
  var settings by remember { mutableStateOf(EngineSettings()) }
  Caption(
    "Il cursore muove insieme diffusione e velo di ogni vetro nuovo: a 0 il vetro è quasi limpido e il testo dietro si legge " +
      "piegato dalla lente, a 1 è una lastra del colore dello sfondo. A metà è il vetro di serie, al bit. " +
      "Valore ora: ${"%.2f".format(live.cursor)}.",
  )
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidGlassIconButton(
        onClick = {},
        backdrop = stage,
        modifier = Modifier.then(report.place("tondo")).adaptiveProbe(probeSource, "tondo"),
      ) {
        FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito")
        AppearanceLog("tondo")
      }
      TintedSlab(stage, probeSource, report, "tinto")
    }
    FluidFloatingCapsule(
      modifier = Modifier.then(report.place("capsula")).adaptiveProbe(probeSource, "capsula"),
      backdrop = stage,
    ) {
      FluidCapsuleAction(FluidIcons.Share, "Condividi", onClick = {})
      FluidCapsuleAction(FluidIcons.Filled.Favorite, "Preferito", onClick = {})
      FluidCapsuleAction(FluidIcons.Delete, "Elimina", onClick = {}, destructive = true)
    }
    MenuPanel(stage, probeSource, report, "pannello", width = 240.dp, height = 140.dp, compact = false)
  }
  Spacer(Modifier.height(16.dp))
  // La riga vera dell'engine, collegata al cursore: il valore lo conserva la pagina (come farebbe un'app).
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .height(280.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.background)
      .testTag("adaptive-appearance"),
  ) {
    fluidAppearanceSection(
      settings = settings,
      onSettingsChange = { settings = it },
      title = null,
      pureBlack = false,
      accent = false,
      haptics = false,
      glassPreference = live.cursor,
      onGlassPreferenceChange = { live.cursor = it },
    )
  }
  LaunchedEffect(live) {
    snapshotFlow { (live.cursor * 20f).roundToInt() }.collect {
      Log.i(AdaptiveLogTag, "CURSORE valore=${"%.2f".format(live.cursor)}")
    }
  }
}

/**
 * Il vetro di serie, il tinto e `Clear` accanto, sopra una foto chiara (in alto) e una scura (in basso). **`Clear` non ha
 * ancora un componente che lo posi**: il tipo `FluidGlass.Clear` e la ricetta ci sono, ma il modificatore che li usa
 * (`Modifier.fluidGlass`) nasce in F3.2. Il riquadro tratteggiato dice dove andrà.
 */
@Composable
private fun VariantsScene(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
) {
  Caption(
    "In alto una foto chiara, in basso una scura. Per riga: il vetro di serie, il tinto e il posto di Clear, che non è " +
      "ancora collegato a nessun componente (arriva con Modifier.fluidGlass, F3.2).",
  )
  VariantsRow(stage, probeSource, report, "chiara")
  Spacer(Modifier.height(220.dp))
  VariantsRow(stage, probeSource, report, "scura")
}

@Composable
private fun VariantsRow(
  stage: GlassBackdropState,
  probeSource: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop,
  report: AdaptiveReport,
  photo: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidGlassIconButton(
      onClick = {},
      backdrop = stage,
      modifier = Modifier.then(report.place("serie-$photo")).adaptiveProbe(probeSource, "serie-$photo"),
    ) {
      FluidIcon(FluidIcons.Filled.Star, contentDescription = "Di serie")
      AppearanceLog("serie-$photo")
    }
    TintedSlab(stage, probeSource, report, "tinto-$photo", width = 96.dp)
    Box(
      modifier = Modifier
        .size(96.dp, 48.dp)
        .drawBehind {
          drawRoundRect(
            color = Color.Gray,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
              width = 2.dp.toPx(),
              pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            ),
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      Text("Clear", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
    }
  }
}

// ------------------------------------------------------------------------------------------------------------
// Lo scorrimento su foto e testo
// ------------------------------------------------------------------------------------------------------------

private enum class BlockKind { Light, Dark, PhotoOrange, PhotoBlue, Text }

/**
 * Una lista lunga che alterna riquadri chiari, scuri, foto sature e testo, dentro una `FluidScreen` vera: la barra coi tasti
 * in alto e la tab bar della galleria in basso. È la scena del **bordo dello scorrimento** (sotto Liquid la fascia di vetro
 * non c'è più: il contenuto si scioglie nel colore del fondo) e del giro mentre si scorre. Con [AdaptiveConfig.fullBar] i
 * riquadri scuri sono alti e riempiono tutta la zona della barra. «Automatico» scorre su e giù piano da solo.
 */
@Composable
private fun ScrollScene(
  config: AdaptiveConfig,
  live: AdaptiveLive,
  bottomInset: Dp,
  onBack: () -> Unit,
  onChange: (AdaptiveConfig) -> Unit,
) {
  val listState = rememberLazyListState()
  val density = LocalDensity.current
  val photo = rememberPhotoBrush()
  val kinds = remember { List(12) { BlockKind.entries[it % BlockKind.entries.size] } }
  FluidScreen(
    title = "Adattività",
    subtitle = "F2.6 · ${config.key}",
    onBack = onBack,
    listState = listState,
    extraBottomPadding = bottomInset,
    actions = {
      FluidBarAction(FluidIcons.Add, "Scena successiva", onClick = { onChange(config.copy(scene = config.scene.next())) })
      FluidBarAction(FluidIcons.Settings, "Barra piena", onClick = { onChange(config.copy(fullBar = !config.fullBar)) })
    },
    modifier = Modifier.testTag("adaptive-scroll"),
  ) {
    item(key = "controls") {
      Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        FluidGlassButton(
          text = "Tema: ${if (config.dark) "scuro" else "chiaro"}",
          onClick = { onChange(config.copy(dark = !config.dark)) },
          modifier = Modifier.testTag("adaptive-theme"),
        )
        Spacer(Modifier.height(8.dp))
        FluidGlassButton(
          text = "Automatico: ${if (live.auto) "acceso" else "spento"}",
          onClick = { live.auto = !live.auto },
          modifier = Modifier.testTag("adaptive-auto"),
        )
        Spacer(Modifier.height(8.dp))
        FluidGlassButton(
          text = "Barra piena: ${if (config.fullBar) "sì" else "no"}",
          onClick = { onChange(config.copy(fullBar = !config.fullBar)) },
          modifier = Modifier.testTag("adaptive-fullbar"),
        )
      }
    }
    kinds.forEachIndexed { index, kind ->
      item(key = "block-$index") { ScrollBlock(kind, index, config.fullBar, photo) }
    }
  }
  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex * 100 + listState.firstVisibleItemScrollOffset / 100 }.collect {
      Log.i(
        AdaptiveLogTag,
        "SCORRIMENTO indice=${listState.firstVisibleItemIndex} scarto=${listState.firstVisibleItemScrollOffset}px " +
          "blocco=${kinds.getOrNull(listState.firstVisibleItemIndex - 1)?.name ?: "-"}",
      )
    }
  }
  // Scorre su e giù piano da solo: 8 s in giù e 8 s in su, a velocità costante (nessun lancio: il giro non tace).
  LaunchedEffect(live.auto, listState) {
    if (!live.auto) return@LaunchedEffect
    val distance = with(density) { 2400.dp.toPx() }
    while (true) {
      listState.animateScrollBy(distance, tween(durationMillis = 8_000, easing = LinearEasing))
      listState.animateScrollBy(-distance, tween(durationMillis = 8_000, easing = LinearEasing))
    }
  }
}

@Composable
private fun ScrollBlock(kind: BlockKind, index: Int, fullBar: Boolean, photo: Brush) {
  val tall = fullBar && kind == BlockKind.Dark
  val height = when {
    tall -> 720.dp
    fullBar && kind == BlockKind.Light -> 120.dp
    kind == BlockKind.Text -> 240.dp
    else -> 220.dp
  }
  val shape = RoundedCornerShape(20.dp)
  when (kind) {
    BlockKind.Light -> Box(Modifier.fillMaxWidth().height(height).clip(shape).background(Color(0xFFF7F5EF)), contentAlignment = Alignment.Center) {
      Text("Riquadro chiaro $index", color = Color(0xFF121214), style = MaterialTheme.typography.titleMedium)
    }
    BlockKind.Dark -> Box(Modifier.fillMaxWidth().height(height).clip(shape).background(Color(0xFF0B0B0D)), contentAlignment = Alignment.Center) {
      Text("Riquadro scuro $index", color = Color(0xFFF2F2F5), style = MaterialTheme.typography.titleMedium)
    }
    BlockKind.PhotoOrange -> Box(
      Modifier
        .fillMaxWidth()
        .height(height)
        .clip(shape)
        .drawBehind {
          drawRect(Brush.linearGradient(listOf(Color(0xFFFF7A00), Color(0xFFFFC21A), Color(0xFFE5340B))))
          drawRect(photo, alpha = 0.35f)
        },
      contentAlignment = Alignment.BottomStart,
    ) {
      Text("Foto satura, arancio $index", color = Color.White, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
    BlockKind.PhotoBlue -> Box(
      Modifier
        .fillMaxWidth()
        .height(height)
        .clip(shape)
        .drawBehind {
          drawRect(Brush.linearGradient(listOf(Color(0xFF0A3FD8), Color(0xFF12B5E5), Color(0xFF2B0A8F))))
          drawRect(photo, alpha = 0.35f)
        },
      contentAlignment = Alignment.BottomStart,
    ) {
      Text("Foto satura, blu $index", color = Color.White, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
    BlockKind.Text -> Column(Modifier.fillMaxWidth().height(height).padding(vertical = 4.dp)) {
      repeat(7) {
        Text(
          "Signs are made using neon lights. Light is used to define color, tone, and shape. They are irreplaceable, " +
            "and depend on the people who bend them all over the world.",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------------------
// Gli automatismi e il registro del fondo
// ------------------------------------------------------------------------------------------------------------

/**
 * Ciò che si muove da solo con `adaptive.auto true`: il fondo bianco → grigio → nero → grigio → bianco ogni 2 s (prima
 * scena), la fascia nera che sale e scende (soglia e grandi), la scacchiera che si sposta di una frazione di casella ogni
 * 600 ms. Spenta, **non scrive niente**.
 */
@Composable
private fun AutomaticMotion(config: AdaptiveConfig, live: AdaptiveLive) {
  LaunchedEffect(config, live.auto) {
    if (!live.auto) return@LaunchedEffect
    when (config.scene) {
      AdaptiveScene.Flip -> {
        var step = 0
        while (true) {
          live.tone = AdaptiveTone.atStep(step++)
          delay(2_000L)
        }
      }
      AdaptiveScene.Threshold, AdaptiveScene.Large -> {
        // Su in 8 s e giù in 8 s, a quota continua: il vetro gira a una quota da una parte e torna da un'altra.
        val start = withFrameMillis { it }
        while (true) {
          val t = (withFrameMillis { it } - start) / 8_000f
          val phase = t % 2f
          live.share = if (phase < 1f) phase else 2f - phase
        }
      }
      AdaptiveScene.Checker -> {
        while (true) {
          live.phase = 1 - live.phase
          delay(600L)
        }
      }
      else -> Unit
    }
  }
}

/** Scrive `FONDO` a ogni cambio del fondo (con l'orologio dei risvegli della sonda), e `COPERTURA` per la fascia scura. */
@Composable
private fun FundLog(config: AdaptiveConfig, live: AdaptiveLive, report: AdaptiveReport) {
  LaunchedEffect(config, live) {
    snapshotFlow { Triple(live.tone, live.dark, live.cell to live.phase) }.collect { (tone, dark, cell) ->
      val what = when (config.scene) {
        AdaptiveScene.Flip -> "tono=${tone.key}"
        AdaptiveScene.Checker -> "riquadri-scuri=${dark.key} casella=${cell.first}dp spostamento=${cell.second}"
        else -> null
      }
      if (what != null) Log.i(AdaptiveLogTag, "FONDO $what t=${android.os.SystemClock.uptimeMillis()}")
    }
  }
  if (config.scene == AdaptiveScene.Threshold) {
    LaunchedEffect(live, report) {
      snapshotFlow { (live.share * 20f).roundToInt() }.collect { report.logCoverage(live.share) }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
