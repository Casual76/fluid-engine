package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Demo isolata: aggregazione generica, sostituzione e scadenza applicativa; non registrata nel catalogo. */
@Composable
fun NotificationUndoDemo() {
  var dark by remember { mutableStateOf(false) }
  var large by remember { mutableStateOf(false) }
  var reduced by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("Nessuna operazione") }
  var serial by remember { mutableIntStateOf(0) }
  var protectedUntil by remember { mutableLongStateOf(0L) }
  val host = rememberFluidNotificationHostState()
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  fun publish(protect: Boolean, ordinary: Boolean = true) {
    val id = "demo-${++serial}"
    if (protect) protectedUntil = android.os.SystemClock.elapsedRealtime() + 10_000L
    val actions = buildList {
      if (protectedUntil > android.os.SystemClock.elapsedRealtime()) {
        add(FluidNotificationAction("Gestisci operazioni in attesa") {
          status = "Operazioni in attesa aperte" // L'app apre qui una destinazione durevole.
        })
      }
      if (ordinary) add(FluidNotificationAction("Ripristina ultima modifica") {
        status = "Ultima modifica ripristinata"
        // La nuova presentazione conserva la destinazione protetta dopo il click dell'altra azione.
        if (protectedUntil > android.os.SystemClock.elapsedRealtime()) publish(false, ordinary = false)
      })
    }
    val n = FluidNotification(id, "Operazione aggiornata", "Ultima modifica disponibile",
      durationMillis = 15_000, inlineActions = actions, replaceGroup = "operazioni")
    scope.launch { status = host.showForResult(n).toString() }
    scope.launch {
      val wait = protectedUntil - android.os.SystemClock.elapsedRealtime()
      if (wait > 0) {
        delay(wait)
        if (android.os.SystemClock.elapsedRealtime() >= protectedUntil) {
          host.updateCurrent(n.copy(message = "Attesa terminata", inlineActions = if (ordinary) actions.takeLast(1) else emptyList()))
        }
      }
    }
  }
  FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
    CompositionLocalProvider(
      LocalDensity provides Density(density.density, if (large) 2f else 1f),
      LocalFluidMotionPolicy provides FluidMotionPolicy(if (reduced) 0f else 1f),
    ) {
      Box(Modifier.fillMaxSize()) {
        FluidScreen(title = "Azioni immediate") {
          item { Text(status) }
          item { FluidButton("Modifica ordinaria", onClick = { publish(false) }) }
          item { FluidButton("Operazione protetta (10 s)", onClick = { publish(true) }) }
          item { FluidButton("Errore", onClick = {
            scope.launch { host.show(FluidNotification("errore-${++serial}", "Operazione non riuscita",
              "Riprova quando disponibile", tone = FluidNotificationTone.Error)) }
          }) }
          item { FluidButton("Chiaro / scuro", onClick = { dark = !dark }) }
          item { FluidButton("Testo 100% / 200%", onClick = { large = !large }) }
          item { FluidButton("Movimento ridotto", onClick = { reduced = !reduced }) }
        }
        FluidNotificationHost(host)
      }
    }
  }
}
