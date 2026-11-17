package dev.antigravity.fluidengine.sample.plane

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.sample.measure.generateMeasurePhoto
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidConcentricShape
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassFlightRecord
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPieceRecord
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidContainerShape
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.fluidGlassUnion
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBackIosNew
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * La pagina «Piano flottante» (piano «iOS in Android», F3.6): dove si guarda, e si riprende dal tablet, ciò che F3 ha
 * dato all'engine. Un `FluidGlassContainer` per scena, e la scena cambia **solo lo stato** (un `if`, una distanza, un
 * fondo): chi nasce da chi, chi compare per lente, chi si fonde, il collo, il contenuto che si sfoca e il giro uniforme li
 * decide il contenitore. La pagina non scrive nessuna animazione.
 *
 * Diciassette scene, ognuna con il suo extra di lancio (`plane.scene`) e a mano dal tasto «Scena»: le leggi e i comandi
 * sono nel `LEGGIMI.md` della cartella. In logcat (`adb logcat -s FluidPiano`) la pagina scrive `SCENA`, `STATO` (ogni cambio di
 * stato, con l'istante), `VIAGGIO` (partito o ripartito, chi va dove), `FINE` (il riposo d'arrivo, coi raggi risolti),
 * `RIPOSO` e `RIQUADRO` (dove sta il contenitore nella finestra).
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page plane --es plane.scene uno-due --es plane.auto true
 *     adb logcat -s FluidPiano
 *
 * **Ferma, la pagina non scrive niente e non chiede fotogrammi**: lo stato cambia solo dal tasto della scena o col
 * cambio automatico (`plane.auto`). `GlassProbeQuietTest` la conta come le altre.
 */
@Composable
internal fun PlanePage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val debuggable = remember(context) { context.isDebuggableApp() }
  val inheritedModel = LocalFluidGlassModel.current
  val inheritedDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val launched = remember(activity, inheritedModel) {
    if (debuggable && activity != null) {
      PlaneLaunch.parse(inheritedDark, inheritedModel) { activity.intent?.getStringExtra(it) }
    } else {
      PlaneConfig(dark = inheritedDark, model = inheritedModel) to PlaneStart()
    }
  }
  val live = remember { PlaneLive(autoMs = launched.second.autoMs, interruptMs = launched.second.interruptMs) }
  PlaneContent(launched.first, live, bottomInset, onBack)
}

/** La pagina senza la lettura degli extra: è ciò che apre il test, con la scena che vuole. */
@Composable
internal fun PlaneContent(initial: PlaneConfig, live: PlaneLive, bottomInset: Dp, onBack: () -> Unit) {
  var config by remember { mutableStateOf(initial) }
  FluidTheme(settings = EngineSettings(themeMode = if (config.dark) ThemeMode.DARK else ThemeMode.LIGHT), systemBars = false) {
    CompositionLocalProvider(
      LocalFluidGlassModel provides config.model,
      LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      // La manopola della distanza di fusione (F3.2c): vale per tutti i contenitori finché la pagina è aperta.
      DisposableEffect(config.fusion) {
        FluidGlassContainerDiagnostics.fusionPreview = config.fusion
        onDispose { FluidGlassContainerDiagnostics.fusionPreview = null }
      }
      // La scena rinasce (e il suo stato riparte da capo) solo se cambia lei, il tema, lo spacing o il vetro chiesto.
      key(config) {
        PlaneStage(config, live, bottomInset, onBack, onChange = { live.reset(); config = it })
      }
    }
  }
}

/** Il contenitore della scena visto dalla finestra, per la riga `RIQUADRO`. Non è stato: scriverlo non ricompone. */
private class PlaneFrame {
  var bounds: Rect? = null
}

@Composable
private fun PlaneStage(
  config: PlaneConfig,
  live: PlaneLive,
  bottomInset: Dp,
  onBack: () -> Unit,
  onChange: (PlaneConfig) -> Unit,
) {
  val stage = rememberGlassBackdrop()
  val photo = rememberPhotoBrush()
  val frame = remember { PlaneFrame() }
  val scope = rememberCoroutineScope()
  // La scala delle animazioni di sistema (`animator_duration_scale`): è il «lento» di questa pagina. Il contenitore, i
  // viaggi di Fluid-physics e il contenuto che si sfoca la leggono già; qui allunga allo stesso modo i tempi della pagina
  // (il passo dell'interruzione, l'intervallo dell'automatico), perché «a metà viaggio» resti a metà.
  val scale = LocalFluidMotionPolicy.current.durationScale
  val command: () -> Unit = remember(config, live, scale) { { runCommand(scope, config, live, scale) } }

  AutoCommand(config, live, scale, command)
  JourneyLog(config, live, frame, scale)

  Box(modifier = Modifier.fillMaxSize().testTag("plane-page")) {
    // Il fondo: la foto della galleria; nelle scene del fondo un velo chiaro o scuro, letto in disegno (cambiarlo non
    // ricompone niente: il vetro resta fermo e cambia ciò che ha dietro).
    Box(
      modifier = Modifier
        .fillMaxSize()
        // Il fondo cambia solo in disegno, dentro il layer della sorgente: dalla 4.0 (F3.10) lo dice `glassBackdropSource`
        // da sé (`glassSourceRedraws`, dentro il layer), e le sonde dei vetri rileggono senza che la pagina annunci niente.
        .glassBackdropSource(stage)
        .drawBehind { fund(photo, config.scene, live.on) },
    )
    CompositionLocalProvider(LocalGlassBackdrop provides stage, LocalFluidCanvasBackdrop provides stage) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .padding(
            start = FluidMetrics.ScreenEdgeMargin,
            end = FluidMetrics.ScreenEdgeMargin,
            top = 8.dp,
            bottom = bottomInset + FluidFloatingTabBarDefaults.ContentInset + 16.dp,
          ),
      ) {
        Controls(config, live, scale, onBack, onChange, command)
        Spacer(Modifier.height(8.dp))
        Caption(config.scene.voice)
        Spacer(Modifier.height(24.dp))
        SceneBody(config, live, frame, command)
      }
    }
  }
}

/** Il comando della scena: un cambio di stato, o tre (due a metà viaggio) se l'interruzione è accesa. */
private fun runCommand(scope: CoroutineScope, config: PlaneConfig, live: PlaneLive, scale: Float) {
  val log: (Int) -> Unit = { step -> logState(config.scene, step) }
  val gap = live.interruptMs
  if (gap <= 0L) {
    live.change(log)
    return
  }
  scope.launch {
    val scaled = (gap * scale.coerceAtLeast(0.01f)).toLong().coerceAtLeast(16L)
    live.change(log)
    delay(scaled)
    live.change(log)
    delay(scaled)
    live.change(log)
  }
}

private fun logState(scene: PlaneScene, step: Int) {
  Log.i(PlaneLogTag, "STATO ${scene.key} passo=$step ${describe(scene, step % 2 == 1)} t=${SystemClock.uptimeMillis()}")
}

/** Lo stato della scena in parole, per la riga `STATO` e per il tasto. */
internal fun describe(scene: PlaneScene, on: Boolean): String = when (scene) {
  PlaneScene.OneTwo, PlaneScene.Interrupt -> if (on) "due-tasti" else "un-tasto"
  PlaneScene.Capsules -> if (on) "fuse" else "divise"
  PlaneScene.Appear -> if (on) "lontano-presente" else "lontano-assente"
  PlaneScene.Union -> if (on) "unite" else "separate"
  PlaneScene.Context -> if (on) "contesto-B" else "contesto-A"
  PlaneScene.Uniform, PlaneScene.Clear -> if (on) "fondo-scuro" else "fondo-chiaro"
  PlaneScene.Concentric -> if (on) "margini-larghi" else "margini-stretti"
  PlaneScene.Many -> if (on) "larghi" else "stretti"
  PlaneScene.TwoBorn -> if (on) "tre-tasti" else "un-tasto"
  PlaneScene.Edge -> if (on) "con-i-nuovi" else "senza-i-nuovi"
  PlaneScene.ThreeNear -> if (on) "quattro-tasti" else "tre-tasti"
  PlaneScene.Scrolling -> if (on) "due-tasti-lista-in-basso" else "un-tasto-lista-in-alto"
  PlaneScene.Label -> if (on) "scritta-lunga" else "scritta-corta"
  PlaneScene.Several -> if (on) "entrambi-acceso" else "entrambi-a-riposo"
  PlaneScene.Resize -> if (on) "largo" else "stretto"
}

// ------------------------------------------------------------------------------------------------------------
// I comandi
// ------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
  config: PlaneConfig,
  live: PlaneLive,
  scale: Float,
  onBack: () -> Unit,
  onChange: (PlaneConfig) -> Unit,
  command: () -> Unit,
) {
  Column(modifier = Modifier.plate()) {
    Text("Piano flottante", style = MaterialTheme.typography.headlineSmall)
    Text(
      "F3.6 · ${config.key}" + if (scale != 1f) " · animazioni ×${"%.2f".format(scale)}" else "",
      style = MaterialTheme.typography.bodySmall,
    )
  }
  Spacer(Modifier.height(8.dp))
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing),
    verticalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing),
  ) {
    FluidGlassButton(text = "Indietro", onClick = onBack)
    FluidGlassButton(
      text = "Scena: ${config.scene.title}",
      onClick = { onChange(config.copy(scene = config.scene.next())) },
      modifier = Modifier.testTag("plane-scene"),
    )
    FluidGlassButton(
      text = "Tema: ${if (config.dark) "scuro" else "chiaro"}",
      onClick = { onChange(config.copy(dark = !config.dark)) },
      modifier = Modifier.testTag("plane-theme"),
    )
    FluidGlassButton(
      text = "Spacing: ${PlaneSpacing.label(config.spacing)}",
      onClick = { onChange(config.copy(spacing = PlaneSpacing.next(config.spacing))) },
      modifier = Modifier.testTag("plane-spacing"),
    )
    FluidGlassButton(
      text = "Vetro: ${config.model.name}",
      onClick = {
        onChange(config.copy(model = if (config.model == FluidGlassModel.Liquid) FluidGlassModel.Classic else FluidGlassModel.Liquid))
      },
      modifier = Modifier.testTag("plane-model"),
    )
    FluidGlassButton(
      text = "Automatico: ${if (live.autoMs > 0L) "${live.autoMs} ms" else "spento"}",
      onClick = { live.autoMs = if (live.autoMs > 0L) 0L else PlaneLaunch.DefaultAutoMs },
      modifier = Modifier.testTag("plane-auto"),
    )
    FluidGlassButton(
      text = "Interrompi: ${if (live.interruptMs > 0L) "${live.interruptMs} ms" else "no"}",
      onClick = { live.interruptMs = if (live.interruptMs > 0L) 0L else PlaneLaunch.DefaultInterruptMs },
      modifier = Modifier.testTag("plane-interrupt"),
    )
  }
  Spacer(Modifier.height(12.dp))
  // Il comando della scena: l'unico tasto che cambia lo stato. Dice lo stato di adesso.
  FluidGlassButton(
    text = "${config.scene.command} · ${describe(config.scene, live.on)}",
    onClick = command,
    modifier = Modifier.testTag("plane-command"),
  )
}

/**
 * Una lastra quasi opaca del colore del tema dietro un testo della pagina, come nella pagina «Adattività»: senza, il
 * titolo e le didascalie si perdono sulla foto. Si disegna fuori dal riquadro del testo e non cambia il layout. Non è
 * vetro e non sta nella sorgente: nessun vetro la rifrange e nessuna sonda la legge.
 */
@Composable
private fun Modifier.plate(): Modifier {
  val color = MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
  return this.drawBehind {
    val dx = 8.dp.toPx()
    val dy = 3.dp.toPx()
    drawRoundRect(
      color = color,
      topLeft = Offset(-dx, -dy),
      size = Size(size.width + 2f * dx, size.height + 2f * dy),
      cornerRadius = CornerRadius(FluidRadius.Small.toPx()),
    )
  }
}

@Composable
private fun Caption(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.plate())
}

// ------------------------------------------------------------------------------------------------------------
// Il fondo
// ------------------------------------------------------------------------------------------------------------

/** La foto sintetica della galleria come pennello che si ripete ogni 512 dp (la stessa di «Adattività»). */
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

/** Il velo chiaro e quello scuro delle scene del fondo: la foto «chiara» e la foto «scura». */
private val LightVeil = Color.White.copy(alpha = 0.45f)
private val DarkVeil = Color.Black.copy(alpha = 0.62f)

private fun DrawScope.fund(photo: Brush, scene: PlaneScene, on: Boolean) {
  drawRect(photo)
  when (scene) {
    PlaneScene.Uniform, PlaneScene.Clear -> drawRect(if (on) DarkVeil else LightVeil)
    else -> Unit
  }
}

// ------------------------------------------------------------------------------------------------------------
// Le scene
// ------------------------------------------------------------------------------------------------------------

/** L'altezza di un tasto di vetro (44 dp, la lente di Apple) e il passo dei vetri di una barra. */
private val Lens: Dp = FluidMetrics.GlassButtonLens

@Composable
private fun SceneBody(config: PlaneConfig, live: PlaneLive, frame: PlaneFrame, command: () -> Unit) {
  val ns = rememberFluidGlassNamespace()
  val ns2 = rememberFluidGlassNamespace()
  val on = live.on
  val container = Modifier
    .onGloballyPositioned { frame.bounds = it.boundsInWindow() }
    .testTag("plane-container")
  when (config.scene) {
    PlaneScene.OneTwo, PlaneScene.Interrupt -> FluidGlassContainer(container, spacing = config.spacing) {
      OneTwo(ns, on, command)
    }
    PlaneScene.Capsules -> FluidGlassContainer(container, spacing = config.spacing) {
      // Accostate (nessun varco) sono un grappolo e una sagoma; divise stanno lontane, oltre ogni spacing della scelta.
      Row(horizontalArrangement = Arrangement.spacedBy(if (on) 0.dp else 96.dp)) {
        Capsule("sinistra", "Musica", ns)
        Capsule("destra", "Video", ns)
      }
    }
    PlaneScene.Appear -> FluidGlassContainer(container, spacing = config.spacing) {
      Box(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
          FluidGlassIconButton(onClick = command, modifier = Modifier.fluidGlassId("cerca", ns)) {
            FluidIcon(FluidIcons.Search, "Cerca")
          }
          FluidGlassIconButton(onClick = command, modifier = Modifier.fluidGlassId("preferiti", ns)) {
            FluidIcon(FluidIcons.Star, "Preferiti")
          }
        }
        // Lontano da tutti (dall'altro lato della riga): compare per lente, al suo posto.
        if (on) {
          FluidGlassIconButton(onClick = command, modifier = Modifier.align(Alignment.CenterEnd).fluidGlassId("play", ns)) {
            FluidIcon(FluidIcons.PlayArrow, "Riproduci")
          }
        }
      }
    }
    PlaneScene.Union -> FluidGlassContainer(container, spacing = config.spacing) {
      // Un blocco di tasti dello stesso gruppo, a 8 dp (la distanza fra due vetri di un gruppo): già un grappolo (una
      // cattura), ma tre vetri distinti, separati del tutto a riposo. Con l'unione (la raccomandazione della scheda per i
      // blocchi di tasti, scelta di Alessio del 2026-11-16) sono una sagoma sola anche ferma.
      Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
        listOf(FluidIcons.Favorite, FluidIcons.Mic, FluidIcons.Share).forEachIndexed { index, icon ->
          Box(
            modifier = Modifier
              .fluidGlassId("vista-$index", ns)
              .then(if (on) Modifier.fluidGlassUnion("sagoma", ns) else Modifier)
              .fluidGlass()
              .size(width = 64.dp, height = Lens),
            contentAlignment = Alignment.Center,
          ) { FluidIcon(icon, null) }
        }
      }
    }
    PlaneScene.Context -> FluidGlassContainer(container, spacing = config.spacing) {
      ContextBar(ns, on)
    }
    PlaneScene.Uniform -> FluidGlassContainer(container, spacing = config.spacing) {
      // Tre vetri a 24 dp: tre grappoli (con lo spacing di serie) e una sonda sola, sotto il più grande.
      Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(FluidIcons.ArrowBackIosNew, FluidIcons.Share, FluidIcons.MoreHoriz).forEachIndexed { index, icon ->
          FluidGlassIconButton(onClick = command, modifier = Modifier.fluidGlassId("tasto-$index", ns)) {
            FluidIcon(icon, null)
          }
        }
      }
    }
    PlaneScene.Clear -> Column {
      FluidGlassContainer(container, spacing = config.spacing) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
          Labelled("regular", "Regular", FluidGlass.Regular, ns)
          Labelled("clear", "Clear", FluidGlass.Clear, ns)
        }
      }
      Spacer(Modifier.height(24.dp))
      Caption("Fuori dal contenitore (vetri a sé, non girano):")
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Labelled(null, "Regular", FluidGlass.Regular, ns)
        Labelled(null, "Clear", FluidGlass.Clear, ns)
      }
    }
    PlaneScene.Concentric -> ConcentricPanel(config, ns, on, container)
    PlaneScene.Many -> Column {
      // Quattro tasti: un campo solo in volo (fino a quattro il piano non divide).
      FluidGlassContainer(container, spacing = config.spacing) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (on) 14.dp else 4.dp)) {
          repeat(4) { index ->
            Box(Modifier.fluidGlassId("q$index", ns).fluidGlass().size(Lens))
          }
        }
      }
      Spacer(Modifier.height(24.dp))
      // Otto tasti uniti: un'unione non si divide fino a otto, quindi in volo è un campo solo di otto pezzi (`Body8`
      // pieno), il palco più caro che il piano costruisce.
      FluidGlassContainer(spacing = config.spacing) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (on) 6.dp else 2.dp)) {
          repeat(8) { index ->
            Box(Modifier.fluidGlassId("o$index", ns).fluidGlassUnion("otto", ns).fluidGlass().size(36.dp))
          }
        }
      }
    }
    PlaneScene.TwoBorn -> FluidGlassContainer(container, spacing = config.spacing) {
      TwoBorn(ns, on, command)
    }
    PlaneScene.Edge -> FluidGlassContainer(container, spacing = config.spacing) {
      EdgeRows(ns, on, command)
    }
    PlaneScene.ThreeNear -> FluidGlassContainer(container, spacing = config.spacing) {
      ThreeNear(ns, on, command)
    }
    PlaneScene.Scrolling -> ScrollingBar(config, live, ns, container, command)
    PlaneScene.Label -> FluidGlassContainer(container, spacing = config.spacing) {
      LabelRow(ns, on, command)
    }
    PlaneScene.Several -> Column {
      // Una barra in alto (la scena `uno-due`) e una capsula sotto (la barra del contesto, col contenuto che si sfoca): due
      // contenitori, due namespace, un solo comando. Il vuoto fra i due è oltre ogni spacing della pagina.
      FluidGlassContainer(container, spacing = config.spacing) {
        OneTwo(ns, on, command)
      }
      Spacer(Modifier.height(64.dp))
      FluidGlassContainer(spacing = config.spacing) {
        ContextBar(ns2, on)
      }
    }
    PlaneScene.Resize -> FluidGlassContainer(
      // La misura è dell'app: il contenitore si allarga e si stringe, i pezzi restano gli stessi (stessi `id`, stesso
      // stato) e sono posati ai due capi e in mezzo della riga.
      container.width(if (on) ResizeWide else ResizeNarrow),
      spacing = config.spacing,
    ) {
      ResizeRow(ns, command)
    }
  }
}

/** Il caso tipo della scheda: «Modifica» sempre, «Fatto» accanto quando lo stato è acceso. */
@Composable
private fun OneTwo(ns: FluidGlassNamespace, on: Boolean, command: () -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidGlassIconButton(onClick = command, modifier = Modifier.fluidGlassId("modifica", ns)) {
      FluidIcon(FluidIcons.Edit, "Modifica")
    }
    if (on) {
      Box(
        modifier = Modifier
          .fluidGlassId("fatto", ns)
          .fluidGlass()
          .height(Lens)
          .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
      ) { Text("Fatto", fontWeight = FontWeight.SemiBold) }
    }
  }
}

@Composable
private fun Capsule(id: String, label: String, ns: FluidGlassNamespace) {
  Box(
    modifier = Modifier
      .fluidGlassId(id, ns)
      .fluidGlass()
      .height(Lens)
      .width(112.dp),
    contentAlignment = Alignment.Center,
  ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

/** Un vetro con un'etichetta: con [id] è un pezzo del contenitore in cui sta, senza è un vetro qualsiasi. */
@Composable
private fun Labelled(id: String?, label: String, glass: FluidGlass, ns: FluidGlassNamespace) {
  Box(
    modifier = Modifier
      .then(if (id != null) Modifier.fluidGlassId(id, ns) else Modifier)
      .fluidGlass(glass)
      .size(width = 128.dp, height = 56.dp),
    contentAlignment = Alignment.Center,
  ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

/** Le icone dei due contesti: la prima è la stessa (resta nitida), le altre due cambiano. */
private val ContextA: List<ImageVector> = listOf(FluidIcons.ArrowBackIosNew, FluidIcons.Share, FluidIcons.MoreHoriz)
private val ContextB: List<ImageVector> = listOf(FluidIcons.ArrowBackIosNew, FluidIcons.Edit, FluidIcons.Delete)

/**
 * La barra che cambia contesto: una capsula di tre posti, ognuno con la sua [FluidGlassContentTransition] (la chiave è
 * l'icona, quindi il primo posto non si sfoca), e «Fatto» che nasce accanto nel contesto B.
 */
@Composable
private fun ContextBar(ns: FluidGlassNamespace, on: Boolean) {
  val icons = if (on) ContextB else ContextA
  Row(
    horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier
        .fluidGlassId("barra", ns)
        .fluidGlass()
        .height(Lens)
        .padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      icons.forEachIndexed { slot, icon ->
        // Una transizione per posto: l'icona che non cambia non si sfoca con le altre.
        key(slot) {
          FluidGlassContentTransition(
            targetState = icon,
            modifier = Modifier.size(FluidMetrics.GroupItemPitch, Lens).testTag("plane-slot-$slot"),
            blurReference = FluidMetrics.BarIconSize,
          ) { shown -> FluidIcon(shown, null, size = FluidMetrics.BarIconSize) }
        }
      }
    }
    if (on) {
      Box(
        modifier = Modifier
          .fluidGlassId("fatto", ns)
          .fluidGlass()
          .height(Lens)
          .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
      ) { Text("Fatto", fontWeight = FontWeight.SemiBold) }
    }
  }
}

/**
 * Un pannello di vetro (a sé, fuori da ogni contenitore) che si dichiara contenitore di forma, e dentro un piano con tre
 * vetri concentrici a margini diversi. Il comando allarga tutti i margini di 10 dp: i vetri si spostano e i raggi
 * scendono col margine (raggio del pannello − distanza dal suo bordo, mai sotto 4 dp né oltre mezzo lato corto).
 */
@Composable
private fun ConcentricPanel(config: PlaneConfig, ns: FluidGlassNamespace, on: Boolean, container: Modifier) {
  val panel = ContinuousCornerShape(FluidRadius.Sheet)
  val extra = if (on) 10.dp else 0.dp
  val concentric = FluidConcentricShape(minimum = 4.dp)
  Box(
    modifier = Modifier
      .size(width = 320.dp, height = 208.dp)
      .fluidGlass(shape = panel)
      .fluidContainerShape(panel),
  ) {
    FluidGlassContainer(container, spacing = config.spacing) {
      Box(Modifier.size(width = 320.dp, height = 208.dp)) {
        Box(
          Modifier.align(Alignment.TopStart).padding(8.dp + extra)
            .fluidGlassId("a", ns).fluidGlass(shape = concentric).size(width = 96.dp, height = Lens),
        )
        Box(
          Modifier.align(Alignment.TopEnd).padding(16.dp + extra)
            .fluidGlassId("b", ns).fluidGlass(shape = concentric).size(width = 72.dp, height = Lens),
        )
        Box(
          Modifier.align(Alignment.BottomCenter).padding(24.dp + extra)
            .fluidGlassId("c", ns).fluidGlass(shape = concentric).fillMaxWidth().height(Lens),
        )
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------------------
// Le scene del giro di correzione di F3
// ------------------------------------------------------------------------------------------------------------

/**
 * Lo spazio fra i riquadri da 48 dp di due tasti tondi perché fra i due **vetri** ci siano 8 dp (`GlassSpacing`): le barre
 * vere stanno così (4 dp fra i riquadri, 8 fra i vetri). Il riquadro del tocco è più grande del vetro, e a 8 dp fra i
 * riquadri i vetri starebbero a 12.
 */
private val BarGap: Dp = FluidMetrics.GlassSpacing - (FluidMetrics.TouchTarget - Lens)

/** Un tasto tondo di vetro con il suo `id`: il pezzo di una barra. [onClick] vuoto = un tasto che sta fermo. */
@Composable
private fun RoundKey(
  id: String,
  icon: ImageVector,
  label: String,
  ns: FluidGlassNamespace,
  onClick: () -> Unit = {},
  tag: String? = null,
) {
  // L'`id` va prima di tutto il resto: il tag, se c'è, viene dopo e non cambia a chi appartiene il vetro.
  val modifier = Modifier.fluidGlassId(id, ns).then(if (tag != null) Modifier.testTag(tag) else Modifier)
  FluidGlassIconButton(onClick = onClick, modifier = modifier) { FluidIcon(icon, label) }
}

/**
 * Un tasto ne fa nascere **due**: «Seleziona» si sposta a destra e lascia due tondi nel posto dov'era. Entrambi nascono
 * da lui e dalla stessa parte, e l'orologio li rilascia a due istanti diversi (il più lontano dal suo arrivo per primo,
 * `FluidMotion.PlaneReleaseStagger`). Con un donatore che resta fermo ne può nascere uno solo per lato: i nuovi devono
 * stare entro lo spacing dal posto di **prima** del donatore, e il secondo tondo di fila non ci sta. Per questo il
 * donatore è largo e si sposta.
 */
@Composable
private fun TwoBorn(ns: FluidGlassNamespace, on: Boolean, command: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(BarGap), verticalAlignment = Alignment.CenterVertically) {
    if (on) {
      RoundKey("condividi", FluidIcons.Share, "Condividi", ns, command)
      RoundKey("elimina", FluidIcons.Delete, "Elimina", ns, command)
    }
    FluidGlassButton(text = "Seleziona", onClick = command, modifier = Modifier.fluidGlassId("seleziona", ns))
  }
}

/**
 * Il contenitore **stretto attorno alla riga**, come nelle barre vere: il bordo del contenitore è il bordo dell'ultimo
 * tasto. Due righe della stessa larghezza, a 24 dp una dall'altra (oltre lo spacing di serie: due grappoli):
 *
 *  - sopra, «Modifica» è l'ultimo tasto e ne nasce uno **verso il bordo**: il nuovo tasto sta sul bordo del contenitore
 *    (che si allarga) e il superamento della sua molla esce da dove il contenitore finisce;
 *  - sotto, «Condividi» è l'ultimo tasto e ne nasce uno **verso l'interno**: il nuovo si infila prima di lui e il donatore
 *    va al bordo, spinto e rinculando verso il vuoto dove il nuovo esce.
 */
@Composable
private fun EdgeRows(ns: FluidGlassNamespace, on: Boolean, command: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(BarGap)) {
      RoundKey("indietro", FluidIcons.ArrowBackIosNew, "Indietro", ns)
      RoundKey("modifica", FluidIcons.Edit, "Modifica", ns, command)
      if (on) RoundKey("preferiti", FluidIcons.Star, "Preferiti", ns, command)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(BarGap)) {
      RoundKey("elenco", FluidIcons.MoreHoriz, "Altro", ns)
      if (on) RoundKey("cerca", FluidIcons.Search, "Cerca", ns, command)
      RoundKey("condividi", FluidIcons.Share, "Condividi", ns, command)
    }
  }
}

/**
 * Tre tasti a 8 dp fra i vetri, **senza unione**: già un grappolo (una cattura) ma tre vetri distinti. Il terzo fa
 * nascere un quarto; i primi due non cambiano e non toccano ciò che viaggia, quindi restano al loro vetro di riposo
 * (due fermi vicini non si uniscono perché un terzo compare). Solo il terzo è un comando.
 */
@Composable
private fun ThreeNear(ns: FluidGlassNamespace, on: Boolean, command: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(BarGap), verticalAlignment = Alignment.CenterVertically) {
    RoundKey("cerca", FluidIcons.Search, "Cerca", ns)
    RoundKey("condividi", FluidIcons.Share, "Condividi", ns)
    RoundKey("modifica", FluidIcons.Edit, "Modifica", ns, command)
    if (on) RoundKey("elimina", FluidIcons.Delete, "Elimina", ns, command)
  }
}

/** Quanto è alta la lista della scena `lista`, la riga di una voce e quante voci porta. */
private val ListHeight: Dp = 360.dp
private val ListRowHeight: Dp = 72.dp
private const val ListRows = 40

/** Quanto dura lo scorrimento che accompagna ogni cambio di stato (scalato dalla scala delle animazioni), e di quante righe. */
private const val ListScrollMillis = 1_600
private const val ListScrollRows = 20

/**
 * Un viaggio **sopra una lista che scorre**: il layout canonico delle barre (il contenitore fermo, la lista sotto). Il
 * contenitore sta in un angolo, **attorno alla barra e non alla lista**; la lista è la sorgente del vetro, e a ogni
 * cambio di stato scorre da sola per ~1,6 s (giù se lo stato è acceso, su se è spento) così che il cambio parta con la
 * lista già in moto. Se l'app non muove niente, non si muove niente: lo scorrimento parte solo da un cambio di stato.
 *
 * Perché serve: durante uno scorrimento la cattura del palco va rifatta a ogni fotogramma (la sorgente si ridisegna), ed
 * è il costo di scorrimento che S3 non aveva coperto. Il fondo della pagina non c'entra: la scena ha la sua sorgente.
 */
@Composable
private fun ScrollingBar(
  config: PlaneConfig,
  live: PlaneLive,
  ns: FluidGlassNamespace,
  container: Modifier,
  command: () -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  val listState = rememberLazyListState()
  val rowPx = with(LocalDensity.current) { ListRowHeight.toPx() }
  LaunchedEffect(live.step) {
    // Allo stato di partenza (il lancio, o la scena che rinasce) la lista sta in alto e non si muove.
    if (live.step == 0) return@LaunchedEffect
    val here = listState.firstVisibleItemIndex * rowPx + listState.firstVisibleItemScrollOffset
    val goal = if (live.on) ListScrollRows * rowPx else 0f
    listState.animateScrollBy(goal - here, tween(durationMillis = ListScrollMillis, easing = LinearEasing))
  }
  Box(modifier = Modifier.fillMaxWidth().height(ListHeight)) {
    // Un fondo opaco dentro la sorgente: un testo su trasparenza si rifrange in una macchia (regola di `glassBackdropSource`).
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop).background(MaterialTheme.colorScheme.background),
    ) {
      items(ListRows) { index -> ListRow(index) }
    }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalFluidCanvasBackdrop provides backdrop) {
      FluidGlassContainer(container.align(Alignment.TopStart).padding(12.dp), spacing = config.spacing) {
        OneTwo(ns, live.on, command)
      }
    }
  }
}

/** Una voce della lista: una striscia di un colore vivo, che il vetro rifrange, con la sua scritta. */
@Composable
private fun ListRow(index: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(ListRowHeight)
      .background(Color.hsv((index * 37f) % 360f, 0.55f, 0.92f))
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) { Text("Voce ${index + 1}", color = Color.Black, fontWeight = FontWeight.SemiBold) }
}

/**
 * Un pezzo la cui **scritta cambia lunghezza**: un tasto con testo e lo stesso `id`, che cambia taglia per ciò che dice.
 * Il contenuto nuovo è già della misura d'arrivo mentre il vetro è ancora in viaggio, e viene ritagliato sulla sagoma in
 * volo. Davanti c'è un tondo che sta fermo: non deve muoversi né sentire il viaggio del vicino.
 */
@Composable
private fun LabelRow(ns: FluidGlassNamespace, on: Boolean, command: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(BarGap), verticalAlignment = Alignment.CenterVertically) {
    RoundKey("indietro", FluidIcons.ArrowBackIosNew, "Indietro", ns)
    FluidGlassButton(
      text = if (on) "Salva e chiudi la raccolta" else "Salva",
      onClick = command,
      modifier = Modifier.fluidGlassId("salva", ns),
    )
  }
}

/** Le due larghezze della riga di `ridimensiona`: stretta (i vetri a ~30 dp, oltre lo spacing) e larga. */
private val ResizeNarrow: Dp = 200.dp
private val ResizeWide: Dp = 340.dp

/**
 * Tre tasti posati ai due capi e al centro di una riga che riempie il contenitore. Cambia **solo la misura del
 * contenitore**: i pezzi hanno lo stesso `id` e lo stesso stato, e per la regola del nucleo («se cambia solo il riquadro,
 * i pezzi lo seguono senza viaggio e senza luce») non c'è nessun viaggio. È ciò che succede a una rotazione, a uno
 * schermo diviso, a una barra che sale con la tastiera.
 */
@Composable
private fun ResizeRow(ns: FluidGlassNamespace, command: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RoundKey("indietro", FluidIcons.ArrowBackIosNew, "Indietro", ns, command)
    RoundKey("cerca", FluidIcons.Search, "Cerca", ns, command)
    RoundKey("condividi", FluidIcons.Share, "Condividi", ns, command, tag = "plane-resize-end")
  }
}

// ------------------------------------------------------------------------------------------------------------
// L'automatico e il registro
// ------------------------------------------------------------------------------------------------------------

/**
 * Il cambio automatico, per le riprese: ogni [PlaneLive.autoMs] (moltiplicato per la scala delle animazioni) il comando
 * della scena. Spento, non c'è nessun effetto e la pagina non si muove.
 */
@Composable
private fun AutoCommand(config: PlaneConfig, live: PlaneLive, scale: Float, command: () -> Unit) {
  val every = live.autoMs
  LaunchedEffect(config, every, scale) {
    if (every <= 0L) return@LaunchedEffect
    val wait = (every * scale.coerceAtLeast(1f)).toLong()
    while (true) {
      delay(wait)
      command()
    }
  }
}

/** Ogni quanto si guardano i contatori del contenitore mentre qualcosa viaggia (il passo dell'istante di `FINE`). */
private const val PollMs = 16L

/**
 * Scrive `SCENA`, poi a ogni cambio di stato `VIAGGIO` (partito o ripartito) e `FINE`, dai contatori di
 * `FluidGlassContainerDiagnostics`. Guarda solo dopo un cambio e finché il viaggio non arriva, con un `delay` (che non
 * chiede fotogrammi); a riposo aspetta il prossimo cambio senza fare niente.
 */
@Composable
private fun JourneyLog(config: PlaneConfig, live: PlaneLive, frame: PlaneFrame, scale: Float) {
  LaunchedEffect(config) {
    Log.i(
      PlaneLogTag,
      "SCENA ${config.key} · API ${Build.VERSION.SDK_INT} · animazioni ×${"%.2f".format(scale)} · " +
        "automatico ${if (live.autoMs > 0L) "${live.autoMs} ms" else "spento"} · " +
        "interruzione ${if (live.interruptMs > 0L) "${live.interruptMs} ms" else "no"}",
    )
    fun fresh() = PlaneJourneyWatch(
      FluidGlassContainerDiagnostics.totalJourneys,
      FluidGlassContainerDiagnostics.totalRetargets,
      FluidGlassContainerDiagnostics.totalArrivals,
      FluidGlassContainerDiagnostics.lastRest,
    )
    var watch = fresh()
    // Dopo un cambio si guarda almeno questo tempo (il viaggio parte un fotogramma dopo, quando i pezzi sono misurati).
    val settleMs = (1_200L * scale.coerceAtLeast(1f)).toLong()
    val giveUpMs = (10_000L * scale.coerceAtLeast(1f)).toLong()
    var seen = live.step
    var changedAt = SystemClock.uptimeMillis()
    var startedAt = 0L
    var first = true
    while (true) {
      val now = SystemClock.uptimeMillis()
      val stuck = watch.inFlight && now - startedAt > giveUpMs
      if (stuck) {
        // Un viaggio che non arriva è un difetto da riferire, non un motivo per guardare per sempre: si riparte da capo.
        Log.w(PlaneLogTag, "NESSUNA-FINE dopo ${now - startedAt} ms: il riposo d'arrivo non è stato pubblicato")
        watch = fresh()
      }
      if (!watch.inFlight && now - changedAt > settleMs) {
        if (first) {
          first = false
          logBoxes(frame)
        }
        snapshotFlow { live.step }.first { it != seen }
        changedAt = SystemClock.uptimeMillis()
      }
      delay(PollMs)
      if (live.step != seen) {
        seen = live.step
        changedAt = SystemClock.uptimeMillis()
      }
      val event = watch.observe(
        FluidGlassContainerDiagnostics.totalJourneys,
        FluidGlassContainerDiagnostics.totalRetargets,
        FluidGlassContainerDiagnostics.totalArrivals,
        FluidGlassContainerDiagnostics.lastRest,
      )
      val t = SystemClock.uptimeMillis()
      when (event) {
        PlaneJourneyWatch.Event.Started, PlaneJourneyWatch.Event.Retargeted -> {
          if (event == PlaneJourneyWatch.Event.Started) startedAt = t
          Log.i(
            PlaneLogTag,
            "VIAGGIO ${if (event == PlaneJourneyWatch.Event.Started) "partito" else "ripartito"} " +
              "dopo ${t - changedAt} ms dal cambio · viaggi=${FluidGlassContainerDiagnostics.totalJourneys} " +
              "ripartenze=${FluidGlassContainerDiagnostics.totalRetargets} · ${flights(FluidGlassContainerDiagnostics.lastJourney)} " +
              // F3.10: la luce del dito, il bagliore da cui comincia il campo del vetro toccato (zero senza tocco).
              "dito=${"%.2f".format(java.util.Locale.ROOT, FluidGlassContainerDiagnostics.lastFinger)} t=$t",
          )
        }
        PlaneJourneyWatch.Event.Finished -> Log.i(
          PlaneLogTag,
          "FINE dopo ${t - startedAt} ms dalla partenza · ${rests(FluidGlassContainerDiagnostics.lastRest)} t=$t",
        )
        PlaneJourneyWatch.Event.Rest -> Log.i(PlaneLogTag, "RIPOSO ${rests(FluidGlassContainerDiagnostics.lastRest)} t=$t")
        PlaneJourneyWatch.Event.None -> Unit
      }
    }
  }
}

private fun logBoxes(frame: PlaneFrame) {
  val box = frame.bounds
  if (box != null) {
    Log.i(
      PlaneLogTag,
      "RIQUADRO contenitore x=${box.left.roundToInt()} y=${box.top.roundToInt()} " +
        "w=${box.width.roundToInt()} h=${box.height.roundToInt()} (px della finestra; i pezzi sotto sono relativi a qui)",
    )
  }
  Log.i(
    PlaneLogTag,
    "RIPOSO ${rests(FluidGlassContainerDiagnostics.lastRest)} · contenitori=${FluidGlassContainerDiagnostics.liveContainers} " +
      "sonde=${FluidGlassProbeDiagnostics.liveProbes} avvisi-ordine=${FluidGlassContainerDiagnostics.totalOrderWarnings}",
  )
}

/** I vetri in volo, uno per gruppo di parole: `da->a:Cambio(vicino)`. */
internal fun flights(records: List<FluidGlassFlightRecord>): String = records.joinToString(" ") { r ->
  val neighbour = when {
    r.bornFromId != null -> "(da ${r.bornFromId})"
    r.mergesIntoId != null -> "(in ${r.mergesIntoId})"
    else -> ""
  }
  "${r.fromId ?: "-"}->${r.toId ?: "-"}:${r.change}$neighbour"
}

/** I vetri a riposo: `id[x,y lxa r=tl/tr/br/bl g=grappolo]`, in px del contenitore. */
internal fun rests(records: List<FluidGlassPieceRecord>): String = records.joinToString(" ") { p ->
  val b = p.bounds
  val r = p.cornerRadii
  "${p.id ?: "-"}[${b.left.roundToInt()},${b.top.roundToInt()} ${b.width.roundToInt()}x${b.height.roundToInt()} " +
    "r=${r.topLeft.roundToInt()}/${r.topRight.roundToInt()}/${r.bottomRight.roundToInt()}/${r.bottomLeft.roundToInt()} g=${p.clusterSize}]"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
