package dev.antigravity.fluidengine.sample.measure

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.util.Locale

/**
 * La pagina «Misure» (piano «iOS in Android», pacchetto F0.6).
 *
 * Esiste perché sei punti dell'architettura del vetro nuovo si decidono con dei numeri presi sul
 * tablet, prima di scrivere il materiale: quanto costa un cambio di soli uniform (C1), se Fluid-physics
 * a `Full` regge (C2), quanti pezzi fusi reggono (C3), se il bordo dello scorrimento a due passate sta
 * nel fotogramma (C4), quanto costa leggere una miniatura dello sfondo (C5), quanto dura la prima
 * compilazione (C6). Ogni scena è fissa e ripetibile, e scrive i suoi tempi a schermo e in logcat con
 * il tag `FluidMisure`.
 *
 * Si guida da riga di comando, senza toccare lo schermo, come il giro della galleria:
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page measure --es measure.scene compare --es measure.glass today
 *     adb logcat -s FluidMisure
 *
 * Senza `measure.scene` la pagina mostra l'elenco delle scene, ognuna con i suoi default. Tutti gli
 * extra, i comandi di ogni scena e la mappa cancello → scena → soglia stanno in `LEGGIMI.md`, accanto a
 * questo file. Gli extra si leggono solo nella build di debug, come `gallery.page`.
 */
@Composable
internal fun MeasurePage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val launched = remember(activity) {
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (debuggable && activity != null) MeasureConfig.parse { activity.intent?.getStringExtra(it) } else null
  }
  var picked by remember { mutableStateOf<MeasureConfig?>(null) }
  val config = launched ?: picked
  if (config == null) {
    MeasureMenu(bottomInset = bottomInset, onBack = onBack, onPick = { picked = MeasureConfig.defaults(it) })
  } else {
    // Una scena scelta dall'elenco si lascia con «Indietro» e si torna all'elenco; una lanciata con gli
    // extra resta finché non la si rilancia, e l'«Indietro» è quello della galleria.
    BackHandler(enabled = launched == null) { picked = null }
    key(config) {
      MeasureRun(config = config, window = activity?.window, bottomInset = bottomInset)
    }
  }
}

/** L'elenco delle scene, per chi arriva dalla navigazione della galleria. */
@Composable
private fun MeasureMenu(bottomInset: Dp, onBack: () -> Unit, onPick: (MeasureScene) -> Unit) {
  FluidScreen(
    title = "Misure",
    subtitle = "Scene fisse per i cancelli della prima sessione. I numeri vanno in logcat: adb logcat -s FluidMisure.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
  ) {
    item(key = "measure-scenes") {
      FluidListGroup {
        MeasureScene.entries.forEachIndexed { index, scene ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = scene.title,
            subtitle = "${scene.detail} (measure.scene ${scene.key})",
            onClick = { onPick(scene) },
          )
        }
      }
    }
  }
}

/** Una scena in esecuzione: il suo tempo, i suoi tempi di fotogramma, le sue righe. */
@Composable
private fun MeasureRun(config: MeasureConfig, window: Window?, bottomInset: Dp) {
  val context = LocalContext.current
  val clock = remember { MeasureClock() }
  val report = remember { MeasureReport() }
  val sources = rememberMeasureSources()
  val shaders = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ProtoSources.load(context.resources) else null
  }
  // Una sorgente rifiutata dal driver non fa cadere la pagina: si scrive quale, e il prototipo non si monta.
  val refused = remember(shaders) {
    if (shaders != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) shaders.refused() else emptyMap()
  }
  val meter = remember(window) { window?.let { FrameMeter(it) } }
  DisposableEffect(meter) {
    meter?.start()
    onDispose { meter?.stop() }
  }
  LaunchedEffect(clock) { clock.run() }
  // Prima della scena: chi legge il registro deve sapere su quale schermo sono stati presi i numeri.
  LaunchedEffect(Unit) {
    report.line(deviceLine(context, meter))
    refused.forEach { (name, message) ->
      Log.e(MeasureLogTag, "Sorgente $name rifiutata dal compilatore del dispositivo: $message")
      report.line("ERRORE source=$name rifiutata dal compilatore: ${message.lineSequence().firstOrNull().orEmpty()}")
    }
  }

  val session = remember(config, clock, meter, report, sources, shaders, refused, bottomInset) {
    MeasureSession(
      config = config,
      clock = clock,
      meter = meter,
      report = report,
      sources = sources,
      shaders = shaders.takeIf { refused.isEmpty() },
      // La barra delle schede della galleria resta sopra la pagina: gli elementi le stanno fuori.
      bottomInset = bottomInset + FluidFloatingTabBarDefaults.ContentInset,
    )
  }
  Box(modifier = Modifier.fillMaxSize()) {
    MeasureSceneContent(session)
    if (config.hud) MeasureHud(report)
  }
}

/**
 * Le ultime righe del risultato, sopra la scena.
 *
 * Nero e bianco fissi invece dei colori del tema, di proposito: deve leggersi sopra qualunque fondale
 * di prova, in chiaro e in scuro. Cambia solo ai confini delle finestre; per un video pulito, o per
 * togliere anche questo dal conto, `--es measure.hud off`.
 */
@Composable
private fun BoxScope.MeasureHud(report: MeasureReport) {
  if (report.lines.isEmpty()) return
  Column(
    modifier = Modifier
      .align(Alignment.TopStart)
      .statusBarsPadding()
      .padding(8.dp)
      .widthIn(max = 760.dp)
      .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
      .padding(8.dp),
  ) {
    report.lines.forEach { line ->
      Text(text = line, color = Color.White, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
    }
  }
}

private fun deviceLine(context: Context, meter: FrameMeter?): String {
  val metrics = context.resources.displayMetrics
  val hz = meter?.refreshHz?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "n/d"
  return "DISPOSITIVO model=${Build.MODEL.replace(' ', '_')} sdk=${Build.VERSION.SDK_INT}" +
    " px=${metrics.widthPixels}x${metrics.heightPixels} density=${metrics.density} hz=$hz"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
