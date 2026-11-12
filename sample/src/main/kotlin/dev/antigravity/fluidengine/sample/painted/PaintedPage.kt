package dev.antigravity.fluidengine.sample.painted

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.material.LocalGalleryGlassSwitches
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.fluidStaticGlassSurface
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics

internal const val PaintedLogTag = "FluidDipinto"

/** Che cosa la pagina apre da sola al lancio (`painted.open`): niente, il foglio o l'avviso fuori dall'host. */
internal enum class PaintedOpen { None, Sheet, Alert }

internal object PaintedLaunch {
  const val ExtraOpen = "painted.open"

  /** `sheet`/`foglio` → il foglio, `alert`/`avviso` → l'avviso; altro o niente → nessuno. */
  fun parse(extra: (String) -> String?): PaintedOpen = when (extra(ExtraOpen)?.trim()?.lowercase()) {
    "sheet", "foglio" -> PaintedOpen.Sheet
    "alert", "avviso" -> PaintedOpen.Alert
    else -> PaintedOpen.None
  }
}

/**
 * La pagina «Vetro dipinto» (F1B, giro di correzione): il vetro **dipinto** dove si vede davvero.
 *
 * La galleria ha un `FluidGlassModalHost` alla radice, e con un host `FluidSheet` e `FluidAlert` sono i modali di vetro
 * dell'host: il ramo di piattaforma (il `ModalBottomSheet` del foglio, la `Dialog` dell'avviso), col vetro dipinto e
 * col suo scrim, nelle altre pagine non va mai a schermo. Qui il foglio si apre **senza host**
 * (`LocalFluidGlassModalHostState provides null`, la stessa domanda che `FluidSheet` fa per scegliere il ramo) e
 * l'avviso con `inHost = false`; sulla pagina stanno un tasto e un pannello dipinti (`fluidStaticGlassSurface`).
 * Il materiale è quello che la galleria chiede (`gallery.model`, o l'interruttore qui sopra).
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page painted --es painted.open sheet --es gallery.model classic
 *     adb logcat -s FluidDipinto
 */
@Suppress("DEPRECATION") // FluidSheet: il foglio di piattaforma è proprio ciò che la pagina mostra.
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PaintedPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val launched = remember(context) {
    val activity = context.findActivity()
    if (activity != null && context.isDebuggableApp()) PaintedLaunch.parse { activity.intent?.getStringExtra(it) } else PaintedOpen.None
  }
  var sheetOpen by remember { mutableStateOf(launched == PaintedOpen.Sheet) }
  var alertOpen by remember { mutableStateOf(launched == PaintedOpen.Alert) }
  val model = LocalFluidGlassModel.current
  val switches = LocalGalleryGlassSwitches.current
  val api = FluidGlassDiagnostics.simulatedApiLevel ?: Build.VERSION.SDK_INT

  LaunchedEffect(model, api) { Log.i(PaintedLogTag, "AVVIO vetro chiesto ${model.name} | API effettiva $api") }
  LaunchedEffect(sheetOpen) { if (sheetOpen) Log.i(PaintedLogTag, "FOGLIO aperto senza host (ModalBottomSheet) · ${model.name}") }
  LaunchedEffect(alertOpen) { if (alertOpen) Log.i(PaintedLogTag, "AVVISO aperto senza host (Dialog) · ${model.name}") }

  FluidScreen(
    title = "Vetro dipinto",
    subtitle = "Fuori dall'host · vetro ${model.name} · API $api",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    modifier = Modifier.testTag("painted-page"),
  ) {
    item {
      Text(
        "La galleria ha l'host dei modali alla radice: altrove foglio e avviso sono di vetro vero. Qui si aprono " +
          "fuori dall'host, nella finestra di piattaforma, col vetro dipinto e il suo scrim. Con Classic, sotto " +
          "Android 13 o con le sorgenti rifiutate tornano il foglio e l'avviso della 3.x.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    item {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidGlassButton(
          text = "Foglio senza host",
          onClick = { sheetOpen = true },
          modifier = Modifier.testTag("painted-open-sheet"),
        )
        FluidGlassButton(
          text = "Avviso senza host",
          onClick = { alertOpen = true },
          modifier = Modifier.testTag("painted-open-alert"),
        )
        if (switches != null) {
          FluidGlassButton(
            text = "Vetro: ${model.name}",
            onClick = {
              switches.model = if (model == FluidGlassModel.Liquid) FluidGlassModel.Classic else FluidGlassModel.Liquid
            },
          )
        }
      }
    }
    item {
      Text("Sulla pagina, senza scrim", style = MaterialTheme.typography.titleSmall)
    }
    item {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaintedTile("Tasto dipinto", 160.dp, 44.dp, FluidCapsuleShape, "painted-button")
        PaintedTile("Pannello dipinto", 200.dp, 120.dp, ContinuousCornerShape(28.dp), "painted-panel")
      }
    }
  }

  if (sheetOpen) {
    // Senza host: `FluidSheet` apre il `ModalBottomSheet` di piattaforma, col vetro dipinto e il suo scrim.
    CompositionLocalProvider(LocalFluidGlassModalHostState provides null) {
      FluidSheet(onDismissRequest = { sheetOpen = false }, title = "Foglio di piattaforma") {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Questo foglio sta in una finestra sua, fuori dall'host: non può leggere la pagina, e il suo " +
              "vetro è dipinto. Dentro, un tasto dipinto si posa come un vetro annidato.",
            style = MaterialTheme.typography.bodyMedium,
          )
          PaintedTile("Tasto dipinto nel foglio", 200.dp, 44.dp, FluidCapsuleShape, "painted-nested")
          FluidButton(text = "Chiudi", onClick = { sheetOpen = false }, fillWidth = true)
        }
      }
    }
  }

  if (alertOpen) {
    FluidAlert(
      onDismissRequest = { alertOpen = false },
      title = "Uscire senza salvare?",
      message = "Questo avviso è la Dialog di piattaforma, fuori dall'host: vetro dipinto.",
      inHost = false,
      actions = listOf(
        FluidAlertAction("Annulla", onClick = { alertOpen = false }),
        FluidAlertAction("Esci", onClick = { alertOpen = false }, emphasis = FluidAlertAction.Emphasis.Destructive),
      ),
    )
  }
}

/** Un vetro dipinto della taglia data, con la sua etichetta dentro. */
@Composable
private fun PaintedTile(label: String, width: Dp, height: Dp, shape: Shape, tag: String) {
  Box(
    modifier = Modifier.size(width, height).fluidStaticGlassSurface(shape = shape).testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
