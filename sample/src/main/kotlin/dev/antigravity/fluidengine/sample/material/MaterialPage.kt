package dev.antigravity.fluidengine.sample.material

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.measure.MeasureClock
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/** Lo spazio che il riquadro di stato occupa in cima, quando c'è. */
private val HudSpace = 150.dp

/**
 * La pagina «Materiale» (piano «iOS in Android», pacchetto F1A.9).
 *
 * Esiste perché il materiale nuovo sul pannello vero non l'ha ancora visto nessuno, e chi lo guarderà per primo è un
 * agente che guida il tablet da `adb` e porta ad Alessio video e collage: ogni voce del backlog del dispositivo di F1A
 * deve potersi mettere davanti alla telecamera con un comando, fotografare, e leggere da logcat (tag `FluidMateriale`).
 * Le stesse lastre, coi componenti veri dell'engine, `Classic` accanto a `Liquid`, sui sei fondi di 4.6, ferme o con il
 * fondo che scorre, e un riquadro con ciò che dice la diagnostica.
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page material --es material.scene components --es material.backdrop photo
 *     adb logcat -s FluidMateriale
 *
 * Senza `material.scene` la pagina mostra l'elenco delle scene. Gli extra si leggono solo nella build di debug. La
 * mappa voce del backlog → scena → comando → cosa guardare sta in `LEGGIMI.md`, accanto a questo file.
 */
@Composable
internal fun MaterialPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val switches = LocalGalleryGlassSwitches.current
  val launched = remember(activity) {
    if (context.isDebuggableApp() && activity != null) MaterialConfig.parse { activity.intent?.getStringExtra(it) } else null
  }
  // A mano: negli interruttori della galleria, che sopravvivono a una manopola (fa rinascere tutta la galleria).
  var localPicked by remember { mutableStateOf<MaterialConfig?>(null) }
  val picked = switches?.materialConfig ?: localPicked
  val pick: (MaterialConfig?) -> Unit = { config ->
    if (switches != null) switches.materialConfig = config else localPicked = config
  }
  // Il pannello e la revisione stanno fuori dalla chiave della scena: una scelta fa rinascere la scena, non il pannello.
  var localPanel by remember { mutableStateOf(false) }
  val panelOpen = if (switches != null) switches.materialPanelOpen else localPanel
  val setPanel: (Boolean) -> Unit = { open -> if (switches != null) switches.materialPanelOpen = open else localPanel = open }
  // Senza gli interruttori della galleria (una prova che compone la pagina da sola) una manopola rifà la scena da qui.
  var revision by remember { mutableIntStateOf(0) }
  val config = picked ?: launched
  if (config == null) {
    MaterialMenu(bottomInset = bottomInset, onBack = onBack, onPick = { pick(MaterialConfig.defaults(it)) })
  } else {
    // Una scena cambiata a mano si lascia con «Indietro»: si torna a quella degli extra, o all'elenco.
    BackHandler(enabled = picked != null) { pick(null) }
    key(config, revision) {
      MaterialRun(config = config, bottomInset = bottomInset, onOpenPanel = { setPanel(true) })
    }
    MaterialPanel(
      visible = panelOpen,
      config = config,
      onDismiss = { setPanel(false) },
      onChange = pick,
      onRetune = { revision++ },
    )
  }
}

/** L'elenco delle scene, per chi arriva a mano. */
@Composable
private fun MaterialMenu(bottomInset: Dp, onBack: () -> Unit, onPick: (MaterialScene) -> Unit) {
  FluidScreen(
    title = "Materiale",
    subtitle = "Classic accanto a Liquid, sui sei fondi. Le righe vanno in logcat: adb logcat -s FluidMateriale.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
  ) {
    item(key = "material-switches") { GalleryGlassSwitchesSection() }
    item(key = "material-scenes-header") {
      FluidSectionHeader(
        title = "Scene",
        detail = "Ognuna mette davanti una voce del backlog del dispositivo. Il riquadro in alto apre il pannello delle scelte.",
      )
    }
    item(key = "material-scenes") {
      FluidListGroup(modifier = Modifier.testTag("material-scenes")) {
        MaterialScene.entries.forEachIndexed { index, scene ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = scene.title,
            subtitle = "${scene.detail} (material.scene ${scene.key} · ${scene.voices})",
            modifier = Modifier.testTag("material-scene-${scene.key}"),
            onClick = { onPick(scene) },
          )
        }
      }
    }
  }
}

/** Una scena in scena: il palco, la scena, l'host del menu della pagina, il riquadro e il pannello. */
@Composable
private fun MaterialRun(config: MaterialConfig, bottomInset: Dp, onOpenPanel: () -> Unit) {
  val report = remember { MaterialReport() }
  val clock = remember { MeasureClock() }
  val stage = rememberGlassBackdrop()
  val menu = rememberFluidMorphMenuState()
  val view = LocalView.current
  val finger = remember(view) { MaterialFinger(view) }

  // L'orologio gira solo se qualcosa lo legge: una scena ferma deve restare ferma, per le foto e perché il
  // riscaldamento automatico parte solo a finestra ferma.
  val moving = config.motion == MaterialMotion.Scroll ||
    (config.scene == MaterialScene.Intensity && config.intensity == null && config.cycle == MaterialCycle.Cycle)
  if (moving) LaunchedEffect(clock) { clock.run() }
  LaunchedEffect(config) {
    report.line("AVVIO ${config.key} | ${config.scene.voices}")
  }
  val status = rememberMaterialStatus(config, report)
  val session = remember(config, clock, report, stage, menu, finger, bottomInset) {
    MaterialSession(
      config = config,
      clock = clock,
      report = report,
      stage = stage,
      menu = menu,
      finger = finger,
      // La barra delle schede della galleria resta sopra la pagina: le lastre le stanno fuori.
      bottomInset = bottomInset + FluidFloatingTabBarDefaults.ContentInset,
      topInset = if (config.hud) HudSpace else 0.dp,
    )
  }
  // L'inchiostro del tema per tutto ciò che sta sulla scena (S2-pre, difetto 3): la pagina non è una `FluidScreen` né
  // una `Surface`, e senza questo titoli, scritte e icone senza un colore loro prendevano il nero di Material anche
  // sul vetro scuro. È quello che `FluidScreen` dà al suo contenuto.
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Box(modifier = Modifier.fillMaxSize().testTag("material-page")) {
      MaterialStage(config, clock, stage) { MaterialSceneContent(session) }
      FluidMorphMenuHost(state = menu, backdrop = stage)
      if (config.hud) {
        MaterialHud(
          config = config,
          report = report,
          status = status,
          onOpenPanel = onOpenPanel,
          modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        )
      } else {
        // Senza riquadro (le foto) il pannello si apre toccando l'angolo in alto a destra: niente si disegna lì.
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .size(56.dp)
            .pointerInput(onOpenPanel) { detectTapGestures { onOpenPanel() } }
            .testTag("material-panel-corner"),
        )
      }
    }
  }
}

/**
 * Il pannello delle scelte, per chi tiene il tablet in mano: ogni riga passa al valore dopo. Le stesse scelte degli
 * extra `material.*`, il vetro e l'Android della galleria, e le manopole a varianti del profilo (i numeri si danno da
 * `adb`, con `gallery.tune`).
 */
@Composable
private fun MaterialPanel(
  visible: Boolean,
  config: MaterialConfig,
  onDismiss: () -> Unit,
  onChange: (MaterialConfig) -> Unit,
  onRetune: () -> Unit,
) {
  val context = LocalContext.current
  val switches = LocalGalleryGlassSwitches.current
  FluidGlassModalPortal(
    visible = visible,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Materiale",
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 560.dp)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      FluidSectionHeader(title = "Scena", detail = config.key)
      FluidListGroup {
        CycleRow("Scena", config.scene.title, MaterialScene.entries, config.scene) {
          onChange(MaterialConfig.defaults(it).copy(models = config.models, hud = config.hud))
        }
        FluidListDivider()
        CycleRow("Fondo", config.backdrop.title, MaterialBackdrop.entries, config.backdrop) { onChange(config.copy(backdrop = it)) }
        FluidListDivider()
        CycleRow("Movimento del fondo", config.motion.key, MaterialMotion.entries, config.motion) { onChange(config.copy(motion = it)) }
        FluidListDivider()
        CycleRow("Vetri affiancati", config.models.key, MaterialModels.entries, config.models) { onChange(config.copy(models = it)) }
        FluidListDivider()
        CycleRow("Ciclo", config.cycle.key, MaterialCycle.entries, config.cycle) { onChange(config.copy(cycle = it)) }
        FluidListDivider()
        FluidListRow(title = "Riquadro di stato", subtitle = "Toglilo per le foto", onClick = { onChange(config.copy(hud = !config.hud)) })
      }
      if (switches != null) {
        FluidSectionHeader(title = "Galleria", detail = "Valgono per tutte le pagine")
        FluidListGroup {
          CycleRow("Vetro della radice", switches.model.name, FluidGlassModel.entries, switches.model) { switches.model = it }
          FluidListDivider()
          val levels = listOf(GalleryGlassLaunch.ApiOff, 32, 30)
          val level = switches.apiLevel ?: GalleryGlassLaunch.ApiOff
          CycleRow("Android simulato", if (level == GalleryGlassLaunch.ApiOff) "quello vero" else "$level", levels, level) {
            switches.simulate(context, it.takeIf { l -> l != GalleryGlassLaunch.ApiOff })
          }
          FluidListDivider()
          FluidListRow(
            title = "Riscaldamento",
            subtitle = if (switches.warmUp) "acceso" else "spento",
            onClick = { switches.switchWarmUp(!switches.warmUp) },
          )
        }
      }
      KnobRows(onRetune = onRetune)
      Text(
        text = "I numeri del profilo si danno da adb: --es gallery.tune blur=24,rimalpha=0.4 (elenco nel LEGGIMI).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
      )
    }
  }
}

/** Una riga che a ogni tocco passa al valore dopo di [values]. */
@Composable
private fun <T> CycleRow(title: String, subtitle: String, values: List<T>, current: T, onNext: (T) -> Unit) {
  FluidListRow(
    title = title,
    subtitle = subtitle,
    onClick = {
      val at = values.indexOf(current)
      onNext(values[(at + 1).mod(values.size)])
    },
  )
}

/**
 * Le manopole a varianti del profilo, una riga per manopola: «serie», poi le varianti delle tavole. Ogni tocco fa
 * rinascere la galleria (o la sola scena, se la galleria non ha i suoi interruttori).
 */
@Composable
private fun KnobRows(onRetune: () -> Unit) {
  val context = LocalContext.current
  val switches = LocalGalleryGlassSwitches.current
  val tuning = switches?.tuning ?: FluidGlassDiagnostics.liquidProfileTuning
  val variants = FluidGlassDiagnostics.liquidProfileKnobs.filterValues { values -> values.none { ".." in it } }
  fun apply(next: Map<String, String>) {
    if (switches != null) {
      switches.tune(context, next)
    } else {
      FluidGlassDiagnostics.tuneLiquidProfile(context, next)
      onRetune()
    }
  }
  FluidSectionHeader(
    title = "Manopole del profilo",
    detail = if (tuning.isEmpty()) "Profilo di serie" else GalleryGlassLaunch.tuneText(tuning),
  )
  FluidListGroup {
    variants.entries.forEachIndexed { index, (name, values) ->
      if (index > 0) FluidListDivider()
      val choices = listOf(SerieValue) + values
      val current = tuning[name] ?: SerieValue
      CycleRow(name, current, choices, current) { next ->
        val map = LinkedHashMap(tuning)
        if (next == SerieValue) map.remove(name) else map[name] = next
        apply(map)
      }
    }
    FluidListDivider()
    FluidListRow(title = "Torna al profilo di serie", subtitle = "gallery.tune off", onClick = { apply(emptyMap()) })
  }
}

private const val SerieValue = "serie"

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
